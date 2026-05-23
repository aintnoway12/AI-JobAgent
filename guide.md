코드 정의서 내용을 충분히 파악했습니다. 이제 사용자 프로필(IT/개발 직군, 부산 거주)과 제약 조건에 맞춰 API 호출 방법을 안내해 드리겠습니다.

***

## 📋 API 엔드포인트 개요

공공기관 채용 데이터는 **목록 조회** → **상세 조회** 2단계로 사용합니다.

| 구분 | 엔드포인트 |
|------|----------|
| 목록 조회 | `https://opendata.alio.go.kr/v1/recruit/list.do` |
| 상세 조회 | `https://opendata.alio.go.kr/v1/recruit/detail.do` |

***

## 🔑 공통 파라미터

모든 요청에 아래 파라미터가 필요합니다.

| 파라미터 | 설명 | 예시 |
|---------|------|------|
| `authKey` | 발급받은 인증키 | `"YOUR_API_KEY"` |
| `resultType` | 응답 포맷 | `"json"` |
| `pageNo` | 페이지 번호 | `1` |
| `numOfRows` | 한 페이지 결과 수 | `10` |

***

## 🔍 STEP 1 — 채용 목록 조회

사용자 프로필(IT/개발 직군, 부산 거주, 신입+경력, 학력 무관~대졸)과 제약 조건을 반영한 쿼리입니다.

```json
POST https://opendata.alio.go.kr/v1/recruit/list.do
Content-Type: application/json

{
  "authKey": "YOUR_API_KEY",
  "resultType": "json",
  "pageNo": 1,
  "numOfRows": 20,

  // ✅ NCS분류 고정 (전기.전자 + 정보통신)
  "ncsCode": ["R600019", "R600020"],

  // ✅ 근무지 — 부산 우선, 필요시 확장
  "workRegion": ["R3014"],   // 부산: R3014
  // 광역 필터 확장 예시: ["R3014", "R3022", "R3016"]
  // (부산 + 경남 + 울산 / 동남권 블록)

  // 채용구분 — 신입 또는 신입+경력
  "recruitSe": ["R2010", "R2030"],

  // 고용형태 — 정규직, 계약직, 청년인턴 계열 포함
  "empType": ["R1010", "R1020", "R1050", "R1060", "R1070"],

  // 학력 — 학력무관, 대졸(4년), 석사
  "education": ["R7010", "R7050", "R7060"],

  // ⛔ 기간(종료) 파라미터 미포함
  "startDate": "2026-05-23"   // 기간(시작)만 포함
}
```

> **참고:** `workRegion` 필드에서 **부산(R3014)** 이 기본값입니다. 원격 근무 가능 포지션까지 원하면 **서울(R3010), 경기(R3017)** 를 배열에 추가하세요.
***

## 🔎 STEP 2 — 채용 상세 조회

목록 조회 응답에서 얻은 `recruitId`(공고 ID)를 사용합니다. 

```json
POST https://opendata.alio.go.kr/v1/recruit/detail.do
Content-Type: application/json

{
  "authKey": "YOUR_API_KEY",
  "resultType": "json",
  "recruitId": "공고ID값"   // 목록 응답에서 추출
}
```

***

## 💻 JavaScript 예시 코드

```javascript
const API_KEY = "YOUR_API_KEY";
const BASE_URL = "https://opendata.alio.go.kr/v1/recruit";

// STEP 1: 목록 조회
async function fetchRecruitList() {
  const params = {
    authKey: API_KEY,
    resultType: "json",
    pageNo: 1,
    numOfRows: 20,
    ncsCode: ["R600019", "R600020"],   // 전기.전자, 정보통신
    workRegion: ["R3014"],             // 부산
    recruitSe: ["R2010", "R2030"],     // 신입, 신입+경력
    empType: ["R1010", "R1020", "R1050", "R1060", "R1070"],
    education: ["R7010", "R7050", "R7060"],
    startDate: new Date().toISOString().split("T")[0]
    // ⛔ endDate(기간 종료) 미포함
  };

  const res = await fetch(`${BASE_URL}/list.do`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(params),
  });
  return res.json();
}

// STEP 2: 상세 조회
async function fetchRecruitDetail(recruitId) {
  const res = await fetch(`${BASE_URL}/detail.do`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      authKey: API_KEY,
      resultType: "json",
      recruitId,
    }),
  });
  return res.json();
}

// 실행 예시
(async () => {
  const list = await fetchRecruitList();
  const items = list?.response?.body?.items ?? [];

  for (const item of items) {
    const detail = await fetchRecruitDetail(item.recruitId);
    console.log(detail);
  }
})();
```

***

## 📌 근무지 코드 빠른 참조

필터링 옵션으로 추가하기 좋은 코드들입니다. 
| 지역 | 코드 | 비고 |
|------|------|------|
| 부산 | `R3014` | 기본값 (거주지) |
| 경남 | `R3022` | 인접 광역 |
| 울산 | `R3016` | 인접 광역 |
| 서울 | `R3010` | 원격/혼합 가능 시 추가 |
| 경기 | `R3017` | 원격/혼합 가능 시 추가 |
| 세종 | `R3026` | 공공기관 밀집 |
| 해외 | `R3030` | 글로벌 포지션 원할 경우 |

어떤 지역들을 추가로 포함할지 결정하셨나요?