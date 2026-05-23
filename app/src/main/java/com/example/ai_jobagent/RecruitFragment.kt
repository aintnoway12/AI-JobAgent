package com.example.ai_jobagent

//NOTICE : 수정 - ALIO API 파라미터, 응답 파싱, 지역 코드 등 변경 가능성 있음
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ai_jobagent.databinding.FragmentRecruitBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecruitFragment : Fragment() {

    private var _binding: FragmentRecruitBinding? = null
    private val binding get() = _binding!!

    private val recruitAdapter = RecruitAdapter()

    //NOTICE : 수정 - 지역 코드는 guide.md 기준, 실제 API 코드표 확인 필요
    private val locationOptions = listOf(
        Pair("전국", ""),
        Pair("부산", "R3014"),
        Pair("서울", "R3010"),
        Pair("경기", "R3017"),
        Pair("경남", "R3022"),
        Pair("울산", "R3016"),
        Pair("세종", "R3026"),
        Pair("해외", "R3030")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecruitBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLocationSpinner()
        setupRecyclerView()
        setupCalendarWebView()
        loadRecruits("")

        binding.btnSearch.setOnClickListener {
            val selectedIndex = binding.spinnerLocation.selectedItemPosition
            val regionCode = locationOptions[selectedIndex].second
            loadRecruits(regionCode)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupLocationSpinner() {
        val names = locationOptions.map { it.first }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLocation.adapter = adapter
    }

    private fun setupRecyclerView() {
        binding.rvRecruits.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recruitAdapter
            isNestedScrollingEnabled = false
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupCalendarWebView() {
        //NOTICE : 수정 - 달력 URL 변경 가능
        binding.webViewCalendar.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = WebViewClient()
            isVerticalScrollBarEnabled = false
            setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
            loadUrl("http://www.jobkorea.co.kr/Starter/calendar/sub/month?edu=5")
        }
    }

    private fun loadRecruits(regionCode: String) = lifecycleScope.launch {
        _binding ?: return@launch
        binding.recruitProgressBar.visibility = View.VISIBLE
        binding.rvRecruits.visibility = View.GONE
        binding.tvRecruitEmpty.visibility = View.GONE

        val items = withContext(Dispatchers.IO) {
            fetchRecruitList(regionCode)
        }

        _binding ?: return@launch
        binding.recruitProgressBar.visibility = View.GONE
        if (items.isEmpty()) {
            binding.tvRecruitEmpty.visibility = View.VISIBLE
        } else {
            binding.rvRecruits.visibility = View.VISIBLE
            recruitAdapter.submitList(items)
        }
    }

    //NOTICE : 수정 - 요청 파라미터(ncsCode, recruitSe, empType, education) 및 응답 파싱 로직 변경 가능
    private fun fetchRecruitList(regionCode: String): List<RecruitItem> {
        return try {
            val url = URL(ApiConstants.ALIO_RECRUIT_LIST_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val body = buildRequestBody(regionCode)
            val writer = BufferedWriter(OutputStreamWriter(conn.outputStream, "UTF-8"))
            writer.write(body)
            writer.flush()
            writer.close()

            if (conn.responseCode != HttpURLConnection.HTTP_OK) return emptyList()

            val response = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            parseRecruitList(response)
        } catch (e: Exception) {
            android.util.Log.e("RecruitFragment", "fetchRecruitList failed", e)
            emptyList()
        }
    }

    //NOTICE : 수정 - NCS 코드, 채용구분, 고용형태, 학력 코드는 guide.md 참조
    private fun buildRequestBody(regionCode: String): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val obj = JSONObject().apply {
            put("authKey", ApiConstants.ALIO_API_KEY)
            put("resultType", "json")
            put("pageNo", 1)
            put("numOfRows", 20)
            // NCS 분류: 전기.전자(R600019), 정보통신(R600020) 고정
            put("ncsCode", JSONArray().apply {
                put("R600019")
                put("R600020")
            })
            // 근무지 필터 (전국이면 미포함)
            if (regionCode.isNotEmpty()) {
                put("workRegion", JSONArray().apply { put(regionCode) })
            }
            // 채용구분: 신입(R2010), 신입+경력(R2030)
            put("recruitSe", JSONArray().apply {
                put("R2010")
                put("R2030")
            })
            // 고용형태: 정규직, 무기계약직, 청년인턴 계열
            put("empType", JSONArray().apply {
                put("R1010"); put("R1020"); put("R1050"); put("R1060"); put("R1070")
            })
            // 학력: 학력무관, 대졸(4년), 석사
            put("education", JSONArray().apply {
                put("R7010"); put("R7050"); put("R7060")
            })
            // 기간(시작)만 포함 — 기간(종료) 미포함
            put("startDate", today)
        }
        return obj.toString()
    }

    //NOTICE : 수정 - 응답 JSON 구조 및 필드명은 실제 API 응답 확인 후 수정 필요
    private fun parseRecruitList(json: String): List<RecruitItem> {
        return try {
            val root = JSONObject(json)
            val responseObj = root.optJSONObject("response") ?: return emptyList()
            val body = responseObj.optJSONObject("body") ?: return emptyList()

            // items가 배열인 경우와 객체(item 키 포함) 두 가지 모두 처리
            val itemsRaw = body.opt("items")
            val itemsArray: JSONArray? = when (itemsRaw) {
                is JSONArray -> itemsRaw
                is JSONObject -> itemsRaw.optJSONArray("item")
                else -> null
            }
            itemsArray ?: return emptyList()

            (0 until itemsArray.length()).mapNotNull { i ->
                val item = itemsArray.optJSONObject(i) ?: return@mapNotNull null
                RecruitItem(
                    recruitId  = item.optString("recrutPblntSn"),
                    title      = item.optString("recrutPbancttl"),
                    instName   = item.optString("instNm"),
                    workRegion = item.optString("workRgnCdNm"),
                    recruitType= item.optString("recrutSeCdNm"),
                    startDate  = item.optString("pbancBgngYmd")
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("RecruitFragment", "parseRecruitList failed", e)
            emptyList()
        }
    }
}
