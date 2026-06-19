package com.example.ai_jobagent

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ResumePdfGenerator(private val ctx: Context) {

    // 디자인 색상 팔레트
    private val accent = Color.parseColor("#1E3A5F")   // 진한 네이비 (포인트)
    private val accentLight = Color.parseColor("#2E5A88")
    private val textDark = Color.parseColor("#1E293B")
    private val textGray = Color.parseColor("#475569")
    private val divider = Color.parseColor("#D5DCE4")

    // 페이지 규격 (A4)
    private val pageWidth = 595
    private val pageHeight = 842
    private val marginLeft = 45f
    private val marginRight = 45f
    private val contentRight = pageWidth - marginRight
    private val contentWidth = (pageWidth - marginLeft - marginRight).toInt()

    // imageUrl 매개변수 추가 (Firebase Storage 다운로드 URL 전달 받음)
    suspend fun generateResumePdf(resume: Resume, name: String, imageUrl: String? = null): Uri = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        val typeface = try {
            Typeface.createFromAsset(ctx.assets, "NotoSansKR-Regular.ttf")
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
        // 별도 Bold 폰트가 없으므로 Regular에 BOLD 스타일을 적용
        val typefaceBold = Typeface.create(typeface, Typeface.BOLD)

        // --- Paint 정의 ---
        val titlePaint = TextPaint().apply {
            this.typeface = typefaceBold
            textSize = 30f
            color = accent
            isAntiAlias = true
            letterSpacing = 0.25f
        }
        val namePaint = TextPaint().apply {
            this.typeface = typefaceBold
            textSize = 20f
            color = textDark
            isAntiAlias = true
        }
        val sectionTitlePaint = TextPaint().apply {
            this.typeface = typefaceBold
            textSize = 13.5f
            color = accent
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            this.typeface = typeface
            textSize = 11f
            color = textDark
            isAntiAlias = true
        }
        val labelPaint = TextPaint().apply {
            this.typeface = typeface
            textSize = 11f
            color = textGray
            isAntiAlias = true
        }
        val barPaint = Paint().apply { color = accent; isAntiAlias = true }
        val dividerPaint = Paint().apply {
            color = divider
            strokeWidth = 1f
            isAntiAlias = true
        }
        val photoBorderPaint = Paint().apply {
            color = divider
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            isAntiAlias = true
        }

        // --- 헤더: 제목 / 이름 / 증명사진 ---
        // 제목: "이력서"
        canvas.drawText("이력서", marginLeft, 78f, titlePaint)
        // 이름
        canvas.drawText(name, marginLeft, 112f, namePaint)

        // 증명사진: 우측 상단 (위치 유지)
        val photoWidth = 100f
        val photoHeight = 130f
        val photoLeft = pageWidth - 45f - photoWidth
        val photoTop = 40f
        val photoRect = RectF(photoLeft, photoTop, photoLeft + photoWidth, photoTop + photoHeight)

        if (!imageUrl.isNullOrBlank()) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doInput = true
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            canvas.drawBitmap(bitmap, null, photoRect, null)
                            canvas.drawRect(photoRect, photoBorderPaint)
                            bitmap.recycle()
                            Log.d("ResumePdfGenerator", "이미지 PDF 렌더링 성공")
                        } else {
                            Log.e("ResumePdfGenerator", "비트맵 디코딩 실패")
                        }
                    }
                } else {
                    Log.e("ResumePdfGenerator", "HTTP 에러 코드: ${connection.responseCode}")
                }
            } catch (e: Exception) {
                Log.e("ResumePdfGenerator", "네트워크/이미지 다운로드 예외 발생", e)
            }
        } else {
            Log.w("ResumePdfGenerator", "imageUrl이 비어있거나 null입니다.")
        }

        // 헤더 하단 굵은 구분선 (사진 하단 170f 아래)
        val headerLinePaint = Paint().apply { color = accent; strokeWidth = 2.2f; isAntiAlias = true }
        canvas.drawLine(marginLeft, 184f, contentRight, 184f, headerLinePaint)

        // --- 본문 섹션 렌더링 ---
        var currentY = 214f
        val bottomLimit = pageHeight - 50f

        fun newPageIfNeeded(needed: Float) {
            if (currentY + needed > bottomLimit) {
                doc.finishPage(page)
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                currentY = 55f
            }
        }

        fun drawSection(titleStr: String, contentStr: String) {
            val body = contentStr.ifBlank { "-" }
            val layout = StaticLayout.Builder.obtain(body, 0, body.length, bodyPaint, contentWidth)
                .setLineSpacing(4f, 1f)
                .build()

            // 섹션 제목 + 본문이 들어갈 공간 확보 (없으면 새 페이지)
            newPageIfNeeded(34f + layout.height + 24f)

            // 좌측 액센트 바
            canvas.drawRect(marginLeft, currentY - 11f, marginLeft + 4f, currentY + 3f, barPaint)
            // 제목
            canvas.drawText(titleStr, marginLeft + 13f, currentY, sectionTitlePaint)
            currentY += 11f
            // 제목 밑줄
            canvas.drawLine(marginLeft, currentY, contentRight, currentY, dividerPaint)
            currentY += 17f

            // 본문
            canvas.save()
            canvas.translate(marginLeft, currentY)
            layout.draw(canvas)
            canvas.restore()

            currentY += layout.height + 26f
        }

        // 자기소개: Gemini로 생성 (핵심 강점 기반, 없으면 다른 항목으로 작성)
        val selfIntro = generateSelfIntroduction(resume, name)

        val skillsString = resume.skills.joinToString(", ")

        drawSection("자기소개", selfIntro)
        drawSection("보유 기술 스택", skillsString)
        drawSection("주요 프로젝트 경험 및 성과", resume.projects)
        drawSection("수상 내역", resume.awards)
        drawSection("학점 및 자격증", "GPA: ${resume.gpa}    |    자격증: ${resume.certificates}")

        doc.finishPage(page)

        val fileName = "resume_${System.currentTimeMillis()}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            ctx.contentResolver.openOutputStream(uri)!!.use { doc.writeTo(it) }
            doc.close()
            uri
        } else {
            val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadFolder, fileName)
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            Uri.fromFile(file)
        }
    }

    /**
     * 입력한 핵심 강점 요약을 기반으로 Gemini API가 '자기소개' 문단을 작성한다.
     * 핵심 강점이 비어 있으면 수상 내역, 기술 스택 등 작성된 내용으로 자연스럽게 작성한다.
     */
    private fun generateSelfIntroduction(resume: Resume, name: String): String {
        val prompt = if (resume.highlight.isNotBlank()) {
            """
            다음은 '$name'님의 이력서 핵심 강점입니다. 이를 바탕으로 채용 담당자에게 제출할 이력서의 '자기소개' 문단을 작성해 주세요.

            핵심 강점: ${resume.highlight}
            참고 정보 - 기술 스택: ${resume.skills.joinToString(", ")} / 수상 내역: ${resume.awards} / 주요 프로젝트: ${resume.projects}
            작성 조건:
            - 3~4문장의 자연스러운 한국어 자기소개 문단
            - 1인칭 시점으로, 과장 없이 전문적이고 신뢰감 있는 어조
            - 제목이나 머리말 없이 본문 문단만 출력
            - 최소 300자의 길이로 작성
            """.trimIndent()
        } else {
            """
            '$name'님의 이력서 정보를 바탕으로 채용 담당자에게 제출할 이력서의 '자기소개' 문단을 작성해 주세요.

            - 기술 스택: ${resume.skills.joinToString(", ")}
            - 주요 프로젝트: ${resume.projects}
            - 수상 내역: ${resume.awards}
            - 자격증: ${resume.certificates}

            작성 조건:
            - 3~4문장의 자연스러운 한국어 자기소개 문단
            - 1인칭 시점으로, 위 정보를 자연스럽게 녹여 전문적이고 신뢰감 있는 어조로 작성
            - 제목이나 머리말 없이 본문 문단만 출력
            - 최소 300자의 길이로 작성
            """.trimIndent()
        }

        val generated = callGemini(prompt)
        if (!generated.isNullOrBlank()) return generated.trim()

        // 네트워크 실패 시 폴백
        return resume.highlight.ifBlank {
            val parts = mutableListOf<String>()
            if (resume.skills.isNotEmpty()) parts.add("${resume.skills.joinToString(", ")} 등의 기술 역량을 보유하고 있습니다")
            if (resume.projects.isNotBlank()) parts.add("다양한 프로젝트 경험을 통해 실무 역량을 키워왔습니다")
            if (resume.awards.isNotBlank()) parts.add("관련 분야에서 ${resume.awards}의 성과를 거두었습니다")
            if (parts.isEmpty()) "성실함과 책임감을 바탕으로 꾸준히 성장해 온 지원자입니다." else parts.joinToString(". ") + "."
        }
    }

    private fun callGemini(promptText: String): String? {
        try {
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", promptText)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 1024)
                    put("temperature", 0.7)
                    // gemini-2.5-flash는 thinking 모델이라 추론에 토큰을 소모해
                    // 본문이 잘리는 문제가 있으므로 thinking을 끈다.
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingBudget", 0)
                    })
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
                Log.e("ResumePdfGenerator", "Gemini HTTP ${conn.responseCode}: $err")
                return null
            }

            val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText()
            val candidate = JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)

            // 잘림 여부 로깅 (MAX_TOKENS 등)
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason.isNotEmpty() && finishReason != "STOP") {
                Log.w("ResumePdfGenerator", "Gemini finishReason: $finishReason")
            }

            // parts 전체의 text를 이어 붙인다 (여러 조각으로 올 수 있음)
            val parts = candidate.getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            return sb.toString().ifBlank { null }
        } catch (e: Exception) {
            Log.e("ResumePdfGenerator", "callGemini failed", e)
            return null
        }
    }
}
