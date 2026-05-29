package com.example.ai_jobagent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ai_jobagent.databinding.FragmentAiChatBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AiChatFragment : Fragment() {

    private data class HistoryItem(val role: String, val text: String)

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private val chatAdapter = ChatAdapter()
    private val messages = mutableListOf<ChatMessage>()
    private val history = mutableListOf<HistoryItem>()
    private var systemPrompt = ""
    private var isLoading = false
    private var interviewActive = false
    private var userMessageCount = 0
    private var currentResume: Resume? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth

        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = chatAdapter
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty() && !isLoading) {
                sendUserMessage(text)
                binding.etMessage.text?.clear()
            }
        }

        binding.btnNewInterview.setOnClickListener { startNewInterview() }
        binding.btnViewHistory.setOnClickListener { loadPreviousInterview() }
        binding.btnEndInterview.setOnClickListener { endInterview() }

        if (currentResume == null) {
            checkResumeAndShowButtons()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun checkResumeAndShowButtons() = lifecycleScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch

        val resume = withContext(Dispatchers.IO) {
            try {
                Firebase.firestore.collection("users").document(uid)
                    .collection("resumes")
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .get().await()
                    .documents.firstOrNull()?.toObject(Resume::class.java)
            } catch (e: Exception) {
                null
            }
        }

        if (_binding == null) return@launch

        if (resume == null) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.btnBar.visibility = View.GONE
            binding.chatInputLayout.visibility = View.GONE
            binding.rvChat.visibility = View.GONE
            return@launch
        }

        currentResume = resume
        systemPrompt = buildSystemPrompt(resume)
        binding.tvEmptyState.visibility = View.GONE
        binding.btnBar.visibility = View.VISIBLE
    }

    private fun startNewInterview() {
        currentResume ?: return

        messages.clear()
        history.clear()
        userMessageCount = 0
        interviewActive = true

        chatAdapter.submitList(emptyList())
        binding.rvChat.visibility = View.VISIBLE
        binding.chatInputLayout.visibility = View.VISIBLE
        binding.btnEndInterview.visibility = View.GONE

        lifecycleScope.launch {
            setLoading(true)
            val response = withContext(Dispatchers.IO) {
                callGeminiApi("모의 면접을 시작해 주세요. 첫 번째 질문을 해주세요.")
            }
            setLoading(false)
            addAiMessage(response ?: "면접을 시작하는 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
        }
    }

    private fun loadPreviousInterview() = lifecycleScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch

        val chatDoc = withContext(Dispatchers.IO) {
            try {
                Firebase.firestore.collection("users").document(uid)
                    .collection("chats")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .get().await()
                    .documents.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }

        if (_binding == null) return@launch

        if (chatDoc == null) {
            Toast.makeText(requireContext(), "저장된 면접 내역이 없습니다.", Toast.LENGTH_SHORT).show()
            return@launch
        }

        messages.clear()
        history.clear()
        interviewActive = false
        userMessageCount = 0

        @Suppress("UNCHECKED_CAST")
        val savedMessages = chatDoc.get("messages") as? List<Map<String, String>>
        savedMessages?.forEach { item ->
            val role = item["role"] ?: return@forEach
            val text = item["text"] ?: return@forEach
            messages.add(ChatMessage(content = text, isUser = role == "user"))
        }

        val feedback = chatDoc.getString("feedback")
        if (!feedback.isNullOrEmpty()) {
            messages.add(ChatMessage(content = "[면접 피드백]\n\n$feedback", isUser = false))
        }

        binding.rvChat.visibility = View.VISIBLE
        binding.chatInputLayout.visibility = View.GONE
        binding.btnEndInterview.visibility = View.GONE

        chatAdapter.submitList(messages.toList()) {
            if (_binding != null) binding.rvChat.scrollToPosition(messages.size - 1)
        }
    }

    private fun endInterview() {
        if (!interviewActive) return

        lifecycleScope.launch {
            setLoading(true)
            val feedback = withContext(Dispatchers.IO) {
                callGeminiApi(
                    "면접을 종료합니다. 아래 형식으로 핵심만 간결하게 한국어로 작성해 주세요.\n" +
                    "✅ 잘한 점: (1~2가지, 각 한 문장)\n" +
                    "🔧 개선할 점: (1~2가지, 각 한 문장)\n" +
                    "⭐ 총평: (한 문장)"
                )
            }
            setLoading(false)

            if (_binding == null) return@launch

            val feedbackText = feedback ?: "피드백을 가져오지 못했습니다."

            saveInterviewToDb(feedbackText)
            addAiMessage("[면접 피드백]\n\n$feedbackText")

            interviewActive = false
            binding.btnEndInterview.visibility = View.GONE
            binding.chatInputLayout.visibility = View.GONE

            Toast.makeText(requireContext(), "면접이 종료되어 저장되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun saveInterviewToDb(feedback: String) {
        val uid = auth.currentUser?.uid ?: return

        val messagesList = messages.map {
            mapOf("role" to if (it.isUser) "user" else "model", "text" to it.content)
        }

        val chatData = hashMapOf(
            "messages" to messagesList,
            "feedback" to feedback,
            "createdAt" to Timestamp.now()
        )

        try {
            withContext(Dispatchers.IO) {
                Firebase.firestore.collection("users").document(uid)
                    .collection("chats")
                    .add(chatData).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("AiChat", "saveInterviewToDb failed", e)
        }
    }

    private fun sendUserMessage(text: String) {
        messages.add(ChatMessage(content = text, isUser = true))
        chatAdapter.submitList(messages.toList()) {
            if (_binding != null) binding.rvChat.scrollToPosition(messages.size - 1)
        }

        userMessageCount++
        if (userMessageCount >= 1 && interviewActive) {
            binding.btnEndInterview.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            setLoading(true)
            val response = withContext(Dispatchers.IO) { callGeminiApi(text) }
            setLoading(false)
            addAiMessage(response ?: "답변을 가져오지 못했습니다. 다시 시도해 주세요.")
        }
    }

    private fun addAiMessage(text: String) {
        messages.add(ChatMessage(content = text, isUser = false))
        chatAdapter.submitList(messages.toList()) {
            if (_binding != null) binding.rvChat.scrollToPosition(messages.size - 1)
        }
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !loading
    }

    private fun buildSystemPrompt(resume: Resume): String = """
        당신은 경험 많은 IT 채용 면접관입니다. 지원자의 이력서를 바탕으로 모의 면접을 진행합니다.

        [필수 규칙] 반드시 한국어로만 답변하세요. 영어 사용은 절대 금지입니다. 영어로 질문을 받아도 한국어로만 응답하세요.

        지원자 이력서 정보:
        - 기술 스택: ${resume.skills.joinToString(", ")}
        - 주요 프로젝트: ${resume.projects}
        - 수상 내역: ${resume.awards}
        - 학점: ${resume.gpa}
        - 자격증: ${resume.certificates}
        - 핵심 강점: ${resume.highlight}
        - 추천 직군: ${resume.recommendedKeywords.joinToString(", ")}

        면접 진행 규칙:
        1. 반드시 한국어로만 대화하세요.
        2. 한 번에 하나의 질문만 하세요.
        3. 지원자의 답변에 짧은 피드백을 준 뒤 다음 질문으로 이어가세요.
        4. 기술 질문과 인성/경험 질문을 적절히 섞어주세요.
        5. 전문적이지만 친근한 어조를 유지하세요.
    """.trimIndent()

    private fun callGeminiApi(userText: String): String? {
        for (attempt in 0..1) {
            try {
                val contents = JSONArray().apply {
                    for (item in history) {
                        put(JSONObject().apply {
                            put("role", item.role)
                            put("parts", JSONArray().put(JSONObject().put("text", item.text)))
                        })
                    }
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", userText)))
                    })
                }

                val requestBody = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })
                    put("contents", contents)
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 1024)
                    })
                }

                val url = URL("${ApiConstants.GEMINI_BASE_URL}?key=${ApiConstants.GEMINI_API_KEY}")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.doOutput = true
                conn.connectTimeout = 30000
                conn.readTimeout = 60000

                OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(requestBody.toString()) }

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    val err = try {
                        BufferedReader(InputStreamReader(conn.errorStream, "UTF-8")).readText()
                    } catch (e: Exception) { "unknown" }
                    android.util.Log.e("AiChat", "Gemini HTTP ${conn.responseCode}: $err (attempt $attempt)")
                    continue
                }

                val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText()
                val aiText = JSONObject(responseText)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                history.add(HistoryItem("user", userText))
                history.add(HistoryItem("model", aiText))

                return aiText
            } catch (e: Exception) {
                android.util.Log.e("AiChat", "callGeminiApi attempt $attempt failed", e)
            }
        }
        return null
    }
}
