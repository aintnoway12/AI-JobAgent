package com.example.ai_jobagent

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel

class JobClassifier(ctx: Context) {
    private var interpreter: Interpreter? = null
    private val vocab = mutableMapOf<String, Int>()
    private val labels = listOf(
        "프론트엔드", "백엔드", "인프라", "AI/ML", "데이터",
        "보안", "게임", "QA", "임베디드", "연구",
        "모바일", "DevOps", "DevRel", "SRE", "아키텍처",
        "블록체인", "AR/VR", "로보틱스", "PM/기획", "그래픽/렌더링"
    )

    init {
        try {
            // TFLite 모델 로드
            val afd = ctx.assets.openFd("model.tflite")
            val fis = FileInputStream(afd.fileDescriptor)
            val bb = fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            interpreter = Interpreter(bb)

            // 단어장 로드
            ctx.assets.open("vocab.txt").bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, word ->
                    val cleanWord = word.trim().lowercase()
                    if (cleanWord.isNotEmpty()) {
                        vocab[cleanWord] = index + 1
                    }
                }
            }
            Log.d("JobClassifier", "모델 및 단어장 로드 완료. 단어 수: ${vocab.size}")
        } catch (e: Exception) {
            Log.e("JobClassifier", "초기화 실패 (build.gradle의 noCompress 확인 필요)", e)
        }
    }

    fun getTop5Keywords(text: String): List<String> {
        val currentInterpreter = interpreter
        if (currentInterpreter == null || vocab.isEmpty()) {
            Log.w("JobClassifier", "기본값을 반환합니다. (모델/단어장 미초기화)")
            return listOf("백엔드", "프론트엔드", "AI/ML", "데이터", "인프라")
        }

        // 전처리: 특수문자 제거 후 소문자 변환 및 공백 분할
        val cleanText = text.replace(Regex("[^a-zA-Z0-9가-힣\\s]"), " ").lowercase()
        val tokens = cleanText.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(64)

        // 패딩 처리된 입력 배열 생성
        val inputSpace = FloatArray(64) { i ->
            val token = tokens.getOrNull(i)
            (vocab[token] ?: 0).toFloat()
        }

        val inputArr = arrayOf(inputSpace)
        val outputArr = Array(1) { FloatArray(labels.size) }

        // 모델 추론 실행
        currentInterpreter.run(inputArr, outputArr)

        // 상위 5개 직군 추출
        return outputArr[0].withIndex()
            .sortedByDescending { it.value }
            .take(5)
            .map { labels[it.index] }
    }
}