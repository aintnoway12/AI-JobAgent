package com.example.ai_jobagent

//NOTICE : 수정 - ALIO API 응답 필드명은 실제 API 응답에 따라 달라질 수 있음
data class RecruitItem(
    val recruitId: String = "",
    val title: String = "",
    val instName: String = "",
    val workRegion: String = "",
    val recruitType: String = "",
    val startDate: String = ""
)
