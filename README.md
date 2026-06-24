# JobCoach Ai

AI 기반 취업 준비 지원 Android 애플리케이션 — 이력서 작성부터 AI 직군 추천,
취업 뉴스/기술 트렌드 큐레이션, AI 모의 면접, PDF 이력서 생성까지 한 번에
지원합니다.

## 팀원

| 이름 | 학번 |
|---|---|
| 이시하 | 202322585 |
| 판킨킨자우 | 202455393 |

---

## 목차

1. [Coroutine](#1-coroutine)
2. [다운로드 매니저](#2-다운로드-매니저)
3. [Jetpack](#3-jetpack)
4. [외부 APP 연동](#4-외부-app-연동)
5. [DB](#5-db)
6. [API](#6-api)
7. [머신러닝](#7-머신러닝)
8. [Activity 3개 이상 활용 + Intent 데이터 전달](#8-activity-3개-이상-활용--intent-데이터-전달)
9. [안정성 / 완성도](#9-안정성--완성도)

---

## 1. Coroutine

- 모든 비동기 작업(Firebase Auth/Firestore/Storage 호출, 외부 API 통신,
  TFLite 추론)을 Kotlin Coroutine으로 처리
- `lifecycleScope.launch { ... }` 로 코루틴 시작, `withContext(Dispatchers.IO)` /
  `withContext(Dispatchers.Default)` 로 스레드 분리
- Firebase `Task` → `.await()` 확장 함수(kotlinx-coroutines-play-services)로
  콜백 대신 코루틴 동기화 처리
- **적용 위치**: `MainActivity`, `HomeActivity`, `ResumeWriteActivity`,
  `ResumeFragment`, `NewsFragment`, `TechTrendFragment`, `AiChatFragment`,
  `ResumePdfGenerator` 전체

## 2. 다운로드 매니저

- **Glide**(`com.github.bumptech.glide:glide:4.16.0`) 적용
  - 사용자 증명사진 다운로드 및 표시: `HomeActivity`(드로어 헤더 프로필),
    `ResumeWriteActivity`(프로필 이미지), `ResumePdfGenerator`(PDF 삽입용 비트맵 로드)
  - `circleCrop()`, `placeholder()` 등 Glide 옵션 활용
- PDF 이력서는 **MediaStore API**를 통해 다운로드 폴더
  (`Environment.DIRECTORY_DOCUMENTS`)에 직접 저장하는 방식 사용
  (`ResumePdfGenerator.kt`)

## 3. Jetpack

**Recycler View / Fragment / View Pager / Drawer Layout — 4종 모두 사용**

- **RecyclerView**: `ChatAdapter`(`fragment_ai_chat.xml`), `TechTrendAdapter`
  (`fragment_tech_trend.xml`) 등에서 사용
- **Fragment**: `ResumeFragment`, `NewsFragment`, `TechTrendFragment`,
  `AiChatFragment` (`HomeActivity`에서 add/hide/show 방식으로 전환)
- **ViewPager2**: `NewsFragment`의 `vpNews`(취업 뉴스 카드 슬라이드,
  `fragment_news.xml`)
- **DrawerLayout**: `HomeActivity`(`activity_home.xml`) + `NavigationView`로
  사이드 메뉴 구현

## 4. 외부 APP 연동

- **갤러리(포토 피커) 연동**: `ResumeWriteActivity`의
  `registerForActivityResult(ActivityResultContracts.PickVisualMedia())` 로
  시스템 갤러리 앱을 호출해 증명사진 선택
- **PDF 뷰어 앱 연동**: `ResumeWriteActivity`에서 생성된 PDF를
  `Intent.ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION` 으로 열고,
  `Intent.createChooser()`를 통해 사용자가 외부 PDF 뷰어 앱을 선택해 실행

## 5. DB

- **Firebase Authentication**: 회원 인증(이메일/비밀번호)
- **Firebase Firestore**: `users/{uid}` 프로필 문서, `users/{uid}/resumes`
  서브컬렉션(이력서), `users/{uid}/chats` 서브컬렉션(모의면접 기록) 구조로
  CRUD 전반 구현(생성/조회/수정/삭제 — 닉네임 변경, 회원탈퇴 시 문서 삭제 등)
- **Firebase Storage**: 증명사진 파일(`profile/{uid}.jpg`) 업로드/다운로드
  URL 발급

## 6. API

사용 중인 외부 API 3종 (`ApiConstants.kt`에서 키/URL 관리):

| API | 호출 위치 | 용도 |
|---|---|---|
| 네이버 검색(뉴스) Open API | `NewsFragment.fetchNaverNews()` | 추천 직군 키워드로 취업 뉴스 검색 |
| Google Gemini API (`generateContent`, `gemini-2.5-flash`) | `AiChatFragment.callGeminiApi()` | 이력서 기반 AI 모의 면접 질의응답 및 피드백 생성 |
| Stack Exchange(Stack Overflow) Search API | `TechTrendFragment.fetchStackOverflow()` | 보유 기술 스택 관련 최신 기술 Q&A 트렌드 조회 |

- 3개 모두 `HttpURLConnection` 기반 REST 통신 + JSON 파싱(`org.json`)으로 직접 구현

## 7. 머신러닝

- TensorFlow Lite(`org.tensorflow:tensorflow-lite:2.14.0`) 기반 직군 분류
  모델을 TensorFlow로 직접 학습하여 `model.tflite` + `vocab.txt` 형태로 변환,
  앱 내부(`JobClassifier.kt`)에서 온디바이스 추론에 적용

**모델 학습** (`SW_mlmodel.ipynb`)
- 20개 직군 라벨 정의(프론트엔드, 백엔드, AI/ML, 인프라 등) + 라벨별
  strong/weak 키워드 사전 구축 → vocab 생성
- strong 키워드 3배 가중치로 5~15개 단어를 샘플링해 라벨당 200개, 총 4,000개
  가상 이력서 데이터 합성
- `Embedding → GlobalAveragePooling1D → Dense(64) → Dense(20, softmax)`
  구조로 학습 후 TFLite 변환

**앱 내 추론** (`JobClassifier.kt`)
- 이력서 텍스트를 동일한 vocab 규칙으로 토큰화 → `FloatArray(64)` 입력
- `Interpreter.run()`으로 20개 직군 확률 출력 → 상위 5개를
  `recommendedKeywords`로 반환, 뉴스/PDF 등 앱 전반에 사용

## 8. Activity 3개 이상 활용 + Intent 데이터 전달

- **Activity 3개 사용**: `MainActivity`, `HomeActivity`, `ResumeWriteActivity`
  (모두 `AppCompatActivity` 상속)
- **Activity 간 Intent를 통한 데이터 전달**
  1. `MainActivity → HomeActivity`
     - `putExtra(HomeActivity.EXTRA_WELCOME_EMAIL, email)` — 로그인한 이메일 전달
  2. `MainActivity → ResumeWriteActivity`
     - `putExtra(EXTRA_NAME, name)`, `putExtra(EXTRA_EMAIL, email)`
       — 회원가입 직후 이름/이메일을 첫 이력서 작성 화면으로 전달
  3. `ResumeWriteActivity → HomeActivity`
     - `putExtra(HomeActivity.EXTRA_WELCOME_NAME, userName)`,
       `putExtra(HomeActivity.EXTRA_WELCOME_EMAIL, userEmail)`
       — 이력서 생성 완료 후 환영 메시지/드로어 헤더 표시용 데이터 전달
- → 3개 Activity 모두가 서로 Intent로 연결되어 데이터를 주고받는 구조
  (`MainActivity ↔ HomeActivity`, `MainActivity → ResumeWriteActivity → HomeActivity`)

## 9. 안정성 / 완성도

- **안정성**: 외부 API 호출/Firestore 조회/TFLite 추론 등 주요 동작에
  try-catch 예외 처리 및 null 안전 처리
- **완성도**: 회원가입 → 이력서 작성 → AI 추천 → 뉴스/기술트렌드 추천 →
  PDF 생성 → AI 모의면접까지 핵심 기능이 전 화면에서 유기적으로 연결되어
  동작하는 End-to-End 흐름 구현
