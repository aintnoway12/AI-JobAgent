package com.example.ai_jobagent

import com.google.firebase.Timestamp

data class Resume(
    val skills: List<String> = emptyList(),
    val projects: String = "",
    val awards: String = "",
    val gpa: String = "",
    val certificates: String = "",
    val highlight: String = "",
    val recommendedKeywords: List<String> = emptyList(),
    val updatedAt: Timestamp = Timestamp.now()
)