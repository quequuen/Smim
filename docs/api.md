# API 계약

앱과 서버가 주고받는 형태. **mock을 이 형태로 만들면 서버가 붙을 때 교체만 하면 된다.**

- 관련 문서: [`ui-spec.md`](ui-spec.md) · [`location-policy.md`](location-policy.md) · [`tracks.md`](tracks.md)
- 최종 수정: 2026-09-23

> **이 문서의 수명** — 서버에 springdoc을 붙이면 `/swagger-ui.html`이 코드에서 자동 생성된다.
> 그때 아래 **3. 엔드포인트**의 필드 목록은 삭제하고, **1. 설계 원칙**과 **2. 인증**만 남긴다.
> 손으로 쓴 필드 목록을 코드와 나란히 두면 반드시 어긋난다.

---

## 1. 설계 원칙

### 1-1. `author_key`는 어떤 응답에도 포함하지 않는다

기기 익명 키는 삭제·차단·신고 처리에만 쓰는 **서버 내부 값**이다. 응답에 넣는 순간
클라이언트가 같은 사람의 글을 묶을 수 있게 되고, 무기명(D4)이 무너진다.

대신 서버가 계산해서 내려준다.

| 필요한 것 | 노출 대신 |
|---|---|
| 내 글 구분 | `isMine: boolean` |
| 차단 대상 지정 | 클라이언트가 **메시지 id**를 보내면 서버가 작성자를 찾아 처리 |

### 1-2. 시간은 원본만 내려준다

`createdAt`은 ISO 8601로 그대로 준다. **"9개월 전" 같은 문자열을 서버가 만들지 않는다.**
앱이 백그라운드에 오래 머물면 값이 굳기 때문이다. 상대 표기는 렌더링 시점에 앱이 계산한다.

### 1-3. 표식은 숫자로 준다

`marker`는 0~23. 도형·색 매핑은 앱이 한다.

```
도형 = marker % 6     ● ▲ ■ ◆ ★ ⬢
색   = marker / 6     틸 · 클레이 · 앰버 · 슬레이트
```

순서대로 배정하면 도형이 먼저 소진되고 그다음 색이 붙는다 — 색각 이상 대응 규칙이 자동으로 지켜진다.

### 1-4. 좌표는 요청에만, 응답에는 없다

메시지 응답에 좌표를 넣지 않는다. 다른 사용자의 위치를 추정할 수 있게 되기 때문이다.
"이 근처"인지 여부는 조회 결과에 포함된다는 사실 자체로 이미 전달된다.

---

## 2. 인증 — 기기 익명 키

계정이 없다. 기기가 최초 실행 시 난수 키를 만들어 보관하고, 모든 요청에 헤더로 보낸다.

```
X-Device-Key: <base64, 32바이트 난수>
```

- 앱: 최초 실행 시 생성 → `expo-secure-store`에 보관
- 서버: `SHA-256(키)` 를 `author_key`로 저장한다. **원본 키는 저장하지 않는다**
- 키를 잃으면(앱 삭제) 이전 글의 삭제 권한도 사라진다 — 의도된 동작이다

---

## 3. 엔드포인트

### `GET /config`

앱 시작 시 호출. 실패하면 앱의 내장 기본값으로 폴백한다.

```jsonc
{
  "group": "C",              // A/B 실험 그룹. 로그에 함께 기록
  "radiusM": 300,
  "keepRadiusM": 450,        // 로드된 메시지를 유지하는 범위
  "moveThresholdM": 45,      // 화면 재조회를 유발하는 이동 거리
  "heartbeatSec": 45,        // 서버 위치 등록 주기 (화면 갱신과 무관)
  "markerWindowHour": 6
}
```

---

### `GET /messages`

| 파라미터 | 필수 | 설명 |
|---|:---:|---|
| `lat` `lon` | ✓ | 현재 좌표 |
| `radius` | ✓ | `/config`의 `radiusM` |
| `cursor` | | 이 시각보다 **이전** 것을 가져온다 (위로 스크롤) |
| `limit` | | 기본 50 |

```jsonc
{
  "messages": [
    {
      "id": 1287,
      "content": "여기 벚꽃 미쳤다",
      "marker": 0,
      "isMine": false,
      "createdAt": "2025-04-12T14:23:00+09:00",
      "replyTo": {
        "id": 1280,
        "content": "지금은 눈 옴 ㅋㅋㅋ",
        "createdAt": "2025-12-03T09:11:00+09:00"
      }
    }
  ],
  "nextCursor": "2025-04-12T14:23:00+09:00"   // 더 없으면 null
}
```

- 최신순(`createdAt DESC`)
- `status != 'visible'` 인 것과 차단한 작성자의 글은 서버가 걸러서 보낸다
- `replyTo`는 인용 표시에 필요한 만큼만 — 표식이나 `isMine`은 넣지 않는다

---

### `POST /messages`

```jsonc
// 요청
{ "content": "저도 그거 보고 왔어요", "lat": 37.5563, "lon": 126.9238, "replyToId": 1280 }

// 201
{ "id": 1291, "marker": 3, "isMine": true, "createdAt": "2026-09-23T16:02:00+09:00", "replyTo": { ... } }
```

- `content` 1~500자
- `marker`는 **서버가 작성 시점에 확정해 저장**한다 (같은 구역 최근 `markerWindowHour` 내 사용 중인 표식을 피함)
- 금지 구역(`banned_area`) 안이면 `403`

---

### `DELETE /messages/{id}`

본인 글만. 타인 글이면 `403`. → `204`

---

### `POST /messages/{id}/report`

```jsonc
{ "reason": "abuse" }   // abuse | spam | privacy | other
```

같은 사람이 같은 글을 중복 신고할 수 없다(`409`). 누적 임계치를 넘으면 서버가 자동 블라인드한다. → `204`

---

### `POST /blocks`

```jsonc
{ "messageId": 1287 }   // author_key 를 노출하지 않으므로 메시지로 지목한다
```

→ `204`. 해제는 `DELETE /blocks/{blockId}` — 목록은 `GET /blocks`가 id와 함께 내려준다.

---

### `GET /replies`

내 글에 달린 답글. 인앱 표시용(D6).

```jsonc
{
  "replies": [
    {
      "id": 1291,
      "content": "저 왔어요 ㅋㅋ 아직 사람 많아요",
      "marker": 12,
      "createdAt": "2026-09-23T14:02:00+09:00",
      "myMessage": { "id": 1250, "content": "1년 전 사람들 아직 여기 오나" }
    }
  ],
  "unreadCount": 2
}
```

읽음 처리는 앱이 마지막 확인 시각을 로컬에 보관해 판단한다. 서버에 상태를 두지 않는다.

---

### `POST /presence`

하트비트. 실시간 전달 대상에 포함되기 위한 위치 등록이며, **화면 갱신과는 무관**하다.

```jsonc
{ "lat": 37.5563, "lon": 126.9238, "sessionId": "..." }
```

→ `204`. 주기는 `/config`의 `heartbeatSec`.

---

### `POST /logs/session`

세션 종료 시 일괄 전송. 반경 실험용.

```jsonc
{
  "group": "C", "radiusM": 300, "sessionSec": 214,
  "refreshCount": 3, "distanceMovedM": 180, "spanHours": 51840,
  "messagesLoaded": 48, "messagesWritten": 1, "batteryDeltaPct": 2
}
```

> **좌표 원본을 보내지 않는다.** 이동 경로는 민감 정보다. 거리와 횟수 집계만 보낸다.

---

### WebSocket `/ws`

STOMP. 접속 시 `X-Device-Key`로 인증하고 위치를 등록한다.
반경 내에서 새 메시지가 작성되면 서버가 밀어준다. 페이로드는 `GET /messages`의 항목과 동일한 형태.

- 재연결 시 마지막 수신 `createdAt` 이후를 `GET /messages`로 보충한다 (영구 저장이라 복구가 단순하다)

---

## 4. 오류

```jsonc
{ "code": "BANNED_AREA", "message": "이 구역에는 글을 남길 수 없습니다." }
```

| 코드 | HTTP | |
|---|---|---|
| `INVALID_DEVICE_KEY` | 401 | 헤더 없음/형식 오류 |
| `NOT_OWNER` | 403 | 남의 글 삭제 시도 |
| `BANNED_AREA` | 403 | 금지 구역 |
| `ALREADY_REPORTED` | 409 | 중복 신고 |
| `RATE_LIMITED` | 429 | 도배 방지 |

---

## 5. Mock 붙이는 법

클라이언트를 **인터페이스 하나 + 구현 둘**로 나눈다. `PresenceStore`(#8)와 같은 패턴이다.

```
api/
  types.ts        위 응답 타입
  client.ts       interface ApiClient
  mockClient.ts   고정 데이터 반환 (지금)
  httpClient.ts   실제 호출 (서버 생긴 뒤)
  index.ts        플래그로 하나 선택
```

화면 코드는 `ApiClient`만 보므로, 서버가 생기면 `index.ts` 한 줄만 바꾸면 된다.

**mock 데이터는 시간 구분선이 검증되도록 만든다** — 최소 3블록, 그중 하나는 3개월 이상 벌어지게.
