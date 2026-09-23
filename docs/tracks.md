# 구현 트랙 — A / B / +A

접속자 위치를 어디에 둘 것인가(A·B), 그리고 출시 이후 무엇을 할 것인가(+A).

- 관련 문서: [`scope.md`](scope.md), [`location-policy.md`](location-policy.md)
- 최종 수정: 2026-09-23

---

## 0. 정의

| 트랙 | 내용 | 상태 |
|---|---|---|
| **A** | 접속자 위치를 **Redis GEO**에 둔다 | 기획서 기준 설계 |
| **B** | 접속자 위치를 **PostgreSQL**에 둔다 | **구현 시작점** |
| **+A** | 출시 이후의 확장 | 6주차 동결 이후 적재 |

A와 B는 **같은 인터페이스의 두 구현**이다. 메시지·신고·차단 같은 영구 데이터는
어느 쪽이든 PostGIS가 맡으며, A·B가 정하는 것은 *"지금 접속해 있는 사람의 위치"* 하나뿐이다.

> **B로 시작해 8주차에 A를 추가하고 9주차에 비교한다.**
> B가 먼저인 이유는 컨테이너가 하나 줄어 5주차 배포가 단순해지고,
> 초기 규모에서 성능 차이가 체감되지 않기 때문이다.
> A는 포기하지 않는다 — 두 구현을 비교하는 것이 실험 2의 내용이다.

---

## 1. 공통 인터페이스

3주차에 이것부터 만든다. **이 인터페이스가 없으면 8주차에 진짜로 뒤엎게 된다.**

```java
public interface PresenceStore {
    void upsert(String sessionId, String authorKey, Point location);
    void touch(String sessionId);                              // 하트비트
    List<String> findSessionsWithin(Point center, int radiusM);
    void remove(String sessionId);
    int purgeStale(Duration olderThan);
}
```

```yaml
smim:
  presence:
    store: postgres   # postgres | redis
```

### 설계 규칙 두 가지

**① SQL 냄새를 인터페이스에 남기지 않는다**
JPA 엔티티 반환, `Pageable` 파라미터, `EntityManager` 노출 — 전부 금지.
평범한 타입만 주고받는다.

**② PresenceStore 호출을 트랜잭션 밖에 둔다**
Redis는 JPA 트랜잭션에 참여하지 않는다. 메시지 저장(트랜잭션)과
전달 대상 조회(PresenceStore)를 분리해, 저장이 커밋된 뒤 조회한다.

---

## 2. B — PostgreSQL (구현 시작점)

스키마는 `V2__active_session.sql`에 있다.

```sql
CREATE TABLE active_session (
    session_id   VARCHAR(64)            PRIMARY KEY,
    author_key   CHAR(64)               NOT NULL,
    location     GEOGRAPHY(POINT, 4326) NOT NULL,
    updated_at   TIMESTAMPTZ            NOT NULL DEFAULT now()
) WITH (fillfactor = 70);

CREATE INDEX idx_active_session_loc ON active_session USING GIST (location);
```

**전달 대상 조회**

```sql
SELECT session_id FROM active_session
WHERE ST_DWithin(location, ST_MakePoint(:lon, :lat)::geography, :radius)
  AND updated_at > now() - interval '2 minutes';
```

**위치 갱신은 두 경우로 나눈다**

```sql
-- 하트비트 (대부분). 위치를 건드리지 않는다
UPDATE active_session SET updated_at = now() WHERE session_id = :id;

-- 실제 이동 시에만
UPDATE active_session SET location = :point, updated_at = now() WHERE session_id = :id;
```

> `location`에 GiST 인덱스가 걸려 있어 이 컬럼을 UPDATE하면 인덱스도 갱신되고
> HOT update가 불가능해진다. 하트비트 대부분은 제자리이므로 나누면 비용이 크게 줄어든다.
> `updated_at`에는 인덱스를 걸지 않는다.

**만료 처리 (Redis TTL을 대신한다)**

1. WebSocket 연결 종료 시 즉시 `DELETE`
2. 5분마다 `updated_at`이 10분 이상 지난 행 일괄 `DELETE`
3. 조회 쿼리에도 `updated_at` 조건 — 유령 세션 차단

---

## 3. A — Redis GEO

8주차에 구현체 하나를 추가한다. 인터페이스가 있으므로 **반나절~하루** 작업이다.

| 인터페이스 | B · PostgreSQL | A · Redis |
|---|---|---|
| `upsert` | `INSERT ... ON CONFLICT` | `GEOADD` + `EXPIRE` |
| `touch` | `UPDATE updated_at` | `EXPIRE` |
| `findSessionsWithin` | `ST_DWithin` | `GEOSEARCH` |
| `remove` | `DELETE` | `ZREM` |
| `purgeStale` | `DELETE WHERE updated_at <` | 불필요 (TTL 자동) · no-op |

**전환 시 실제로 바뀌는 것**

- 새 파일 `RedisPresenceStore.java` 1개
- 의존성 `spring-boot-starter-data-redis` 1줄
- 설정 `smim.presence.store: redis` 1줄
- `docker compose up -d redis`

바뀌지 않는 것 — WebSocket 핸들러, 메시지 로직, 컨트롤러, 서비스, 앱 코드, 스키마.

### 규모별 판단

접속자 N명, 하트비트 45초 → 초당 위치 쓰기 `N / 45`회.

| 동시 접속 | 초당 쓰기 | 판단 |
|---:|---:|---|
| 100명 | 2회 | 차이 없음 |
| 1,000명 | 22회 | B로 충분 |
| 10,000명 | 222회 | A가 유리해지기 시작 |

---

## 4. +A — 확장

6주차 동결 이후 떠오른 것은 전부 여기에 적고 손대지 않는다.
각 항목에 **재검토 트리거**를 붙여, "우선순위 낮음"이 아니라 조건이 되면 꺼내도록 한다.

### 4-1. 사용자 기능

| 항목 | 미룬 이유 | 재검토 트리거 | 규모 |
|---|---|---|---|
| **FCM 푸시 알림** | Firebase 설정 부담. 인앱 답글 표시로 최소 기능 대체 (D6) | 재방문율이 기대에 못 미칠 때 | 2~3일 |
| **반응 (좋아요 등)** | 없어도 서비스가 성립 | 과거 대표 글을 고르는 기준이 필요해질 때 | 2~3일 |
| **정렬 옵션** (반응순·오래된순) | 시간순 스크롤이 컨셉의 핵심 | 한 장소 메시지가 수백 개를 넘을 때 | 1~2일 |
| **지도 · 핫스팟 뷰** | 채팅 스크롤이 메인. 지도 SDK 의존성을 없애 결제수단 등록까지 제거됨 | "주변에 뭐가 있는지 모르겠다"는 피드백이 반복될 때 | 1주 |

> 지도 뷰를 붙일 때는 **k-익명성 임계값**을 반드시 적용한다.
> 셀 내 인원 10명 미만이면 표시하지 않고, 인원 수 대신 "활발함 / 보통" 단계로만 표현한다.
> 밀도를 그리는 순간 그것은 위치 정보 공개이기 때문이다.

### 4-2. 콘텐츠 품질

| 항목 | 미룬 이유 | 재검토 트리거 | 규모 |
|---|---|---|---|
| **고밀도 지역 시간 축 매몰 대응** | 사용자 수만 명 규모에서 발생 | 한 장소 메시지가 1,000개를 넘을 때 | 3~5일 |
| **과거 대표 글 노출** | 위 항목의 구현 후보 중 하나 | 위와 동일 | 위에 포함 |

> **문제** — 한 장소에 메시지가 수천 개 쌓이면 첫 화면이 당일 것으로만 채워지고,
> 스크롤을 아무리 올려도 오래된 기록에 닿지 못한다. 한적한 지역에서 공간이 무너졌던 것과
> 대칭으로, 번화가에서는 시간 축이 묻힌다.
>
> **후보** — ① 시간 구간별 배분(최신 N개 + 과거 구간별 대표 M개) ② 반응 많은 과거 글을 상단 카드로

### 4-3. 안전 강화

| 항목 | 미룬 이유 | 재검토 트리거 | 규모 |
|---|---|---|---|
| **학교 좌표 일괄 등록** | 데이터 확보 단계가 추가됨. 테이블 구조는 MVP에 포함됨 | 출시 직후 바로 (가장 먼저 꺼낼 항목) | 반나절 |
| **주거전용지역 제외** | GIS 폴리곤 처리·좌표계 변환 비용이 크다 | 주거지역 관련 신고가 발생할 때 | 3~4일 |
| **자동 필터링 고도화** | MVP는 신고 기반 사후 대응만 | 신고량이 수동 검토 한계를 넘을 때 | 1주+ |

> 학교 좌표는 공공데이터포털 **전국초중등학교위치표준데이터**(CSV)로 해결된다.
> 폴리곤이 아니라 점 + 반경 200m면 충분해 비용이 가장 낮다.
> Yik Yak을 무너뜨린 것이 학교 주변 괴롭힘이었으므로, +A 중에서는 **최우선**이다.

### 4-4. 플랫폼 · 인프라

| 항목 | 미룬 이유 | 재검토 트리거 | 규모 |
|---|---|---|---|
| **iOS** | 1인 11주에 두 스토어는 불가능. RN이라 확장 비용은 낮다 | 안드로이드가 안정화되고 요청이 누적될 때 | 2~3주 |
| **서버 다중화** | 단일 서버로 충분한 규모 | 단일 서버 자원 한계에 근접할 때 | 1주 |

> **서버 다중화는 A·B와 무관하게 추가 작업이 필요하다.** 접속자 위치는 공유 저장소에
> 있으므로 "누구에게 보낼지"는 알 수 있지만, 그 사람의 WebSocket 연결이 다른 서버에
> 있으면 직접 보낼 수 없다. **서버 간 릴레이(Redis Pub/Sub 등)가 필요하다.**
> 이전 문서에서 "구조는 이미 가능"이라고 적었던 것은 부정확했다.

### 4-5. 검토했으나 채택하지 않음

미룬 것이 아니라 **방향이 다르다고 판단한 것.** 나중에 흔들리지 않도록 이유를 남긴다.

| 항목 | 판단 |
|---|---|
| **닉네임 · 고정 익명 ID** | 이름은 아무리 무의미해도 정체성을 만들고, 정체성은 관계를 만든다. 벗어나려던 사람 중심 구조로 되돌아간다 (D4) |
| **팔로우 · 프로필 · DM** | 위치 기반이라 같은 사람을 다시 만날 확률이 낮아 관계가 생기지 않는다. 익명의 자유만 잃고 관계는 얻지 못한다. 진짜 관계를 만들려면 다른 서비스가 되며, 오픈카톡·동네생활이 이미 점유하고 있다 (D6) |
| **밀도 기반 동적 반경** | 한적한 지역에서 반경이 수 km로 늘어나 "그 자리"라는 전제가 무너진다 (D2) |
| **메시지 TTL** | 영구 저장이 콜드 스타트를 시간으로 해결하는 장치다. 되돌리면 첫 사용자의 글이 아무에게도 읽히지 않고 사라진다 (D3) |

### 4-6. 운영 (기능 아님)

계속 운영하기로 했으므로 출시 후 필요한 행정 절차.

| 항목 | 기한 |
|---|---|
| 사업자등록 (홈택스, 무료·당일) | 출시 후 1개월 내 |
| 소상공인확인서 발급 | 위와 연계 |
| 위치기반서비스사업 신고 (방통위 · emsit.go.kr) | 출시 후 1개월 내 |

> **등록 전에 확인할 것** — 건강보험 피부양자(1577-1000), 국가장학금 소득분위(1599-2000).
> 사업자등록이 있으면 사업소득이 1원만 생겨도 피부양자 자격을 잃는다.

---

## 5. 진행 순서

```
1~2주차   뼈대 · PostgreSQL만 기동 (docker compose up -d postgres)
3주차     PresenceStore 인터페이스 + B 구현          ← 분기점
4주차     메시지 CRUD
5주차     WebSocket — PresenceStore로 전달 대상 조회 · 서버 1차 배포
6주차     실기기 테스트 → MVP 동결 → 비공개 테스트 개시
7주차     출시 필수 기능
8주차     안정화 + A(RedisPresenceStore) 구현
9주차     A ↔ B 비교 실험 (실험 2)
10주차    최종 배포 · 스토어 제출
11주차    출시 · 발표
그 이후   운영을 A로 전환 · +A 착수 (학교 좌표부터)
```

**3주차가 분기점이다.** 여기서 인터페이스 없이 PostgreSQL을 직접 호출하면
8주차에 서비스·핸들러 곳곳에 박힌 SQL을 걷어내야 한다.
