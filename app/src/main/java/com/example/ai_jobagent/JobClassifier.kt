package com.example.ai_jobagent

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel

class JobClassifier(ctx: Context) {
    private lateinit var interpreter: Interpreter
    private val vocab = mutableMapOf<String, Int>()

    private val labels = listOf("프론트엔드", "백엔드", "인프라", "AI/ML", "데이터", "보안", "게임", "QA", "임베디드", "연구")

    init {
        try {
            val afd = ctx.assets.openFd("model.tflite")
            val fis = FileInputStream(afd.fileDescriptor)
            val bb = fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)

            interpreter = Interpreter(bb)

            ctx.assets.open("vocab.txt").bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, word ->
                    vocab[word.trim().lowercase()] = index + 1
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getTop5Keywords(text: String): List<String> {
        if (!::interpreter.isInitialized || vocab.isEmpty()) {
            return listOf("백엔드", "프론트엔드", "AI/ML", "데이터", "인프라")
        }

        val tokens = text.lowercase().split(Regex("\\s+")).take(64)

        val inputSpace = FloatArray(64) { i ->
            (vocab[tokens.getOrNull(i)] ?: 0).toFloat()
        }

        val inputArr = arrayOf(inputSpace)
        val outputArr = Array(1) { FloatArray(labels.size) }

        interpreter.run(inputArr, outputArr)

        return outputArr[0].withIndex()
            .sortedByDescending { it.value }
            .take(5)
            .map { labels[it.index] }
    }
}