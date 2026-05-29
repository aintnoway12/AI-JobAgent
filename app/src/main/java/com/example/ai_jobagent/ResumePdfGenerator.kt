package com.example.ai_jobagent

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
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
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class ResumePdfGenerator(private val ctx: Context) {

    // imageUrl 매개변수 추가 (Firebase Storage 다운로드 URL 전달 받음)
    suspend fun generateResumePdf(resume: Resume, name: String, imageUrl: String? = null): Uri = withContext(Dispatchers.IO) {
        val doc = PdfDocument()

        // A4 사이즈화 (595 x 842)
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val typeface = try {
            Typeface.createFromAsset(ctx.assets, "fonts/NotoSansKR-Regular.ttf")
        } catch (e: Exception) {
            Typeface.DEFAULT
        }

        val titlePaint = TextPaint().apply {
            this.typeface = typeface
            textSize = 22f
            isAntiAlias = true
        }

        val bodyPaint = TextPaint().apply {
            this.typeface = typeface
            textSize = 12f
            isAntiAlias = true
        }

        // 1. 타이틀 그리기
        canvas.drawText("${name}님의 AI 이력서 보고서", 40f, 60f, titlePaint)

        // 2. Firebase 이미지 다운로드 및 우측 상단 렌더링 (HttpURLConnection 사용으로 안정성 강화)
        if (!imageUrl.isNullOrBlank()) {
            try {
                // 주의: imageUrl은 gs:// 가 아닌 https:// 로 시작하는 다운로드 URL이어야 합니다.
                val url = URL(imageUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doInput = true
                connection.connect()

                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            val targetWidth = 100f
                            val targetHeight = 130f
                            val left = 595f - 40f - targetWidth
                            val top = 40f

                            val destRect = RectF(left, top, left + targetWidth, top + targetHeight)
                            canvas.drawBitmap(bitmap, null, destRect, null)
                            bitmap.recycle() // 메모리 해제
                            Log.d("ResumePdfGenerator", "이미지 PDF 렌더링 성공")
                        } else {
                            Log.e("ResumePdfGenerator", "비트맵 디코딩 실패 (이미지 형식이 아니거나 깨짐)")
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

        // 이미지 공간 확보를 위해 텍스트 시작 높이를 조절합니다.
        var currentY = 190f

        fun drawWrappedSection(titleStr: String, contentStr: String) {
            // 페이지 범위를 벗어나는 것을 방지하는 간단한 안전장치
            if (currentY > 800f) return

            canvas.drawText("■ $titleStr", 40f, currentY, bodyPaint.apply { isFakeBoldText = true })
            currentY += 20f

            // 이미지 영역 침범을 피하기 위해 본문 가로 폭을 515로 지정
            val layout = StaticLayout.Builder.obtain(
                contentStr, 0, contentStr.length,
                bodyPaint.apply { isFakeBoldText = false }, 515
            ).build()

            canvas.save()
            canvas.translate(40f, currentY)
            layout.draw(canvas)
            canvas.restore()

            currentY += layout.height + 30f
        }

        val skillsString = resume.skills.joinToString(", ")
        val keywordsString = resume.recommendedKeywords.joinToString("   |   ")

        // 데이터 그리기 순서
        drawWrappedSection("보유 기술 스택", skillsString)
        drawWrappedSection("주요 프로젝트 경험 성과", resume.projects)
        drawWrappedSection("수상 내역 (Awards)", resume.awards)
        drawWrappedSection("학점 및 자격증 정보", "GPA: ${resume.gpa}   /   자격증: ${resume.certificates}")
        drawWrappedSection("핵심 강점 요약 (Highlight)", resume.highlight)
        drawWrappedSection("AI 에이전트 추천 최적 직군 목록", keywordsString)

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
}