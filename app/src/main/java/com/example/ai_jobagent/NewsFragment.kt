package com.example.ai_jobagent

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.ai_jobagent.databinding.FragmentNewsBinding
import com.google.firebase.auth.FirebaseAuth
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

class NewsFragment : Fragment() {

    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private val newsAdapter = NewsAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = Firebase.auth

        binding.vpNews.adapter = newsAdapter

        if (childFragmentManager.findFragmentById(R.id.recruitContainer) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.recruitContainer, RecruitFragment())
                .commitNow()
        }

        loadNewsData()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            loadNewsData()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadNewsData() = lifecycleScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch

        binding.progressBar.visibility = View.VISIBLE
        binding.vpNews.visibility = View.GONE
        binding.tvKeyword.visibility = View.GONE
        binding.tvEmptyState.visibility = View.GONE

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
            binding.progressBar.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
            return@launch
        }

        val keyword = resume.recommendedKeywords.firstOrNull() ?: "취업"

        val newsList = withContext(Dispatchers.IO) {
            fetchNaverNews(keyword)
        }

        binding.progressBar.visibility = View.GONE

        if (newsList.isEmpty()) {
            binding.tvEmptyState.text = "뉴스를 불러오지 못했습니다."
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.tvKeyword.text = "\"$keyword\" 관련 취업 뉴스"
            binding.tvKeyword.visibility = View.VISIBLE
            binding.vpNews.visibility = View.VISIBLE
            newsAdapter.submitList(newsList)
        }
    }

    private fun fetchNaverNews(keyword: String): List<NaverNewsItem> {
        return try {
            val encodedQuery = URLEncoder.encode("$keyword 취업", "UTF-8") // 목적에 맞게 조정 필요
            val url = URL("${ApiConstants.NAVER_NEWS_URL}?query=$encodedQuery&display=10&sort=date")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-Naver-Client-Id", ApiConstants.NAVER_CLIENT_ID)
            conn.setRequestProperty("X-Naver-Client-Secret", ApiConstants.NAVER_CLIENT_SECRET)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptyList()

            val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            val items = JSONObject(response).getJSONArray("items")

            (0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                NaverNewsItem(
                    title = Html.fromHtml(item.getString("title"), Html.FROM_HTML_MODE_COMPACT).toString(),
                    description = Html.fromHtml(item.getString("description"), Html.FROM_HTML_MODE_COMPACT).toString(),
                    link = item.getString("link"),
                    pubDate = item.getString("pubDate")
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("NewsFragment", "fetchNaverNews failed", e)
            emptyList()
        }
    }
}
