package com.example.ai_jobagent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ai_jobagent.databinding.FragmentAiChatBinding
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
    private var interviewStarted = false

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

        if (!interviewStarted) {
            loadResumeAndStart()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadResumeAndStart() = lifecycleScope.launch {
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

        if (resume == null) {
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.chatInputLayout.visibility = View.GONE
            binding.rvChat.visibility = View.GONE
            return@launch
        }

        binding.tvEmptyState.visibility = View.GONE
        binding.chatInputLayout.visibility = View.VISIBLE
        binding.rvChat.visibility = View.VISIBLE

        systemPrompt = buildSystemPrompt(resume)
        interviewStarted = true
        startInterview()
    }

    private fun buildSystemPrompt(resume: Resume): String = """
        당신은 경험 많은 IT 채용 면접관입니다. 지원자의 이력서를 바탕으로 모의 면접을 진행합니다.

        지원자 이력서 정보:
        - 기술 스택: ${resume.skills.joinToString(", ")}
        - 주요 프로젝트: ${resume.projects}
        - 수상 내역: ${resume.awards}
        - 학점: ${resume.gpa}
        - 자격증: ${resume.certificates}
        - 핵심 강점: ${resume.highlight}
        - 추천 직군: ${resume.recommendedKeywords.joinToString(", ")}

        면접 진행 규칙:
        1. 한 번에 하나의 질문만 하세요.
        2. 지원자의 답변에 짧은 피드백을 준 뒤 다음 질문으로 이어가세요.
        3. 기술 질문과 인성/경험 질문을 적절히 섞어주세요.
        4. 한국어로 대화하세요.
        5. 전문적이지만 친근한 어조를 유지하세요.
    """.trimIndent()

    private fun startInterview() = lifecycleScope.launch {
        setLoading(true)
        val response = withContext(Dispatchers.IO) {
            callGeminiApi("모의 면접을 시작해 주세요. 첫 번째 질문을 해주세요.")
        }
        setLoading(false)
        addAiMessage(response ?: "면접을 시작하는 중 오류가 발생했습니다. Gemini API 키를 확인해 주세요.")
    }

    private fun sendUserMessage(text: String) {
        messages.add(ChatMessage(content = text, isUser = true))
        chatAdapter.submitList(messages.toList())
        binding.rvChat.scrollToPosition(messages.size - 1)

        lifecycleScope.launch {
            setLoading(true)
            val response = withContext(Dispatchers.IO) { callGeminiApi(text) }
            setLoading(false)
            addAiMessage(response ?: "답변을 가져오지 못했습니다. 다시 시도해 주세요.")
        }
    }

    private fun addAiMessage(text: String) {
        messages.add(ChatMessage(content = text, isUser = false))
        chatAdapter.submitList(messages.toList())
        binding.rvChat.scrollToPosition(messages.size - 1)
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !loading
    }

    private fun callGeminiApi(userText: String): String? {
        return try {
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
                android.util.Log.e("AiChat", "Gemini HTTP ${conn.responseCode}: $err")
                return null
            }

            val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText()
            val aiText = JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            // 성공 시에만 history 에 기록
            history.add(HistoryItem("user", userText))
            history.add(HistoryItem("model", aiText))

            aiText
        } catch (e: Exception) {
            android.util.Log.e("AiChat", "callGeminiApi exception", e)
            null
        }
    }
}
