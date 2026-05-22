package com.example.ai_jobagent

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ResumePdfGenerator(private val ctx: Context) {

    suspend fun generateResumePdf(resume: Resume, name: String): Uri = withContext(Dispatchers.IO) {
        val doc = PdfDocument()

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

        canvas.drawText("${name}님의 AI 이력서 보고서", 40f, 60f, titlePaint)

        var currentY = 120f

        fun drawWrappedSection(titleStr: String, contentStr: String) {
            canvas.drawText("■ $titleStr", 40f, currentY, bodyPaint.apply { isFakeBoldText = true })
            currentY += 20f

            val layout = StaticLayout.Builder.obtain(contentStr, 0, contentStr.length, bodyPaint.apply { isFakeBoldText = false }, 515).build()

            canvas.save()
            canvas.translate(40f, currentY)
            layout.draw(canvas)
            canvas.restore()

            currentY += layout.height + 30f
        }

        val skillsString = resume.skills.joinToString(", ")
        val keywordsString = resume.recommendedKeywords.joinToString("   |   ")

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