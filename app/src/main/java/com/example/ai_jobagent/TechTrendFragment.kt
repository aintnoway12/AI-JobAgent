package com.example.ai_jobagent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ai_jobagent.databinding.FragmentTechTrendBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TechTrendFragment : Fragment() {

    private var _binding: FragmentTechTrendBinding? = null
    private val binding get() = _binding!!

    private val adapter = TechTrendAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTechTrendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvTechTrend.adapter = adapter
        loadTechTrends()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadTechTrends() = lifecycleScope.launch {
        val uid = Firebase.auth.currentUser?.uid ?: return@launch

        binding.progressBarTech.visibility = View.VISIBLE
        binding.rvTechTrend.visibility = View.GONE
        binding.tvTechEmptyState.visibility = View.GONE

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

        val query = buildQuery(resume)
        binding.tvTechKeyword.text = "\"$query\" 관련 최신 질문"

        val items = withContext(Dispatchers.IO) {
            fetchStackOverflow(query)
        }

        binding.progressBarTech.visibility = View.GONE

        if (items.isEmpty()) {
            binding.tvTechEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvTechTrend.visibility = View.VISIBLE
            adapter.submitList(items)
        }
    }

    private fun buildQuery(resume: Resume?): String {
        if (resume == null) return "programming"
        val skills = resume.skills.filter { it.isNotBlank() }.take(3)
        val keywords = resume.recommendedKeywords.filter { it.isNotBlank() }.take(2)
        val combined = (skills + keywords).distinct().take(3)
        return if (combined.isEmpty()) "programming" else combined.joinToString(" ")
    }

    private fun fetchStackOverflow(query: String): List<StackOverflowItem> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlStr = "${ApiConstants.SO_SEARCH_URL}?order=desc&sort=relevance&q=$encoded&site=stackoverflow&key=${ApiConstants.SO_API_KEY}"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptyList()

            val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            val items = JSONObject(response).getJSONArray("items")

            (0 until minOf(items.length(), 10)).map { i ->
                val item = items.getJSONObject(i)
                val tagsArr = item.getJSONArray("tags")
                val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }
                StackOverflowItem(
                    title = item.getString("title"),
                    link = item.getString("link"),
                    tags = tags,
                    viewCount = item.optInt("view_count", 0),
                    answerCount = item.optInt("answer_count", 0),
                    score = item.optInt("score", 0),
                    isAnswered = item.optBoolean("is_answered", false)
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("TechTrendFragment", "SO API failed", e)
            emptyList()
        }
    }
}
