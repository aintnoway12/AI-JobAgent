package com.example.ai_jobagent

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val content: String,
    val isUser: Boolean
)
