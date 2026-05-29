package com.example.ai_jobagent

data class StackOverflowItem(
    val title: String,
    val link: String,
    val tags: List<String>,
    val viewCount: Int,
    val answerCount: Int,
    val score: Int,
    val isAnswered: Boolean
)
