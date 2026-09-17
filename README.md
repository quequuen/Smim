# 스밈 (Smim)

> 그 자리에 가야만 읽히는 익명의 기록

사용자의 현재 위치를 중심으로 반경 300m 안에 남겨진 익명 메시지를
시간순으로 읽고 이어 쓰는 공간 기반 커뮤니케이션 서비스입니다.
메시지는 사라지지 않고 좌표에 남아, 나중에 그 자리에 오는 사람에게 읽힙니다.

---

## 핵심 개념

- **방이 없다** — 메시지가 좌표를 가지고, 화면은 내 위치를 중심으로 한 공간 질의의 결과다
- **사라지지 않는다** — 지금 근처에 있는 사람에게는 실시간 대화로, 나중에 오는 사람에게는 기록으로 도착한다
- **이름이 없다** — 닉네임 대신 대화 구간 안에서만 유효한 도형·색 표식으로 발화자를 구분한다
- **공간은 고정, 시간은 무한** — 한적한 곳에서는 반경을 넓히지 않고 더 오래된 기록을 보여준다

## 기술 스택

| 영역 | 기술 |
|---|---|
| 모바일 | React Native (Expo) · `expo-location` |
| 서버 | Spring Boot · Java 21 |
| 실시간 | WebSocket (STOMP) |
| 영속 저장 | PostgreSQL 17 + PostGIS 3.5 |
| 현재 상태 | Redis 7.4 (GEO) |
| 인프라 | Docker Compose · Nginx · Let's Encrypt · Oracle Cloud (ARM) |

## 구조

```
smim/
├── server/              Spring Boot
├── app/                 React Native
├── db/init/             PostgreSQL 최초 실행 스크립트
├── docs/                설계 문서
├── docker-compose.yml   PostGIS + Redis
└── .env.example
```

---

## 시작하기

### 1. 환경변수

```bash
cp .env.example .env
```

`.env`를 열어 비밀번호를 채운다. 이 파일은 docker compose와 Spring Boot가 함께 읽는다.

### 2. 인프라 실행

```bash
docker compose up -d
```

PostGIS가 정상 동작하는지 확인:

```bash
docker compose exec postgres psql -U smim -d smim -c "SELECT postgis_full_version();"
```

### 3. 서버 프로젝트 생성

[start.spring.io](https://start.spring.io)에서 아래 설정으로 생성한 뒤 `server/`에 압축을 푼다.

| 항목 | 값 |
|---|---|
| Project | Gradle |
| Language | Java |
| Java | 21 |
| Group | `com.smim` |
| Artifact | `server` |

**의존성**

- Spring Web
- WebSocket
- Spring Data JPA
- PostgreSQL Driver
- Spring Data Redis
- Flyway Migration
- Validation

생성 후 `build.gradle`에 아래 두 줄을 **직접 추가**한다. Initializr에 항목이 없다.

```gradle
implementation 'org.hibernate.orm:hibernate-spatial'          // GEOGRAPHY 타입 매핑
implementation 'org.flywaydb:flyway-database-postgresql'       // Flyway 10+ 에서 필수
```

그리고 Initializr가 만든 `application.properties`는 **삭제**한다. 이 저장소의 `application.yml`을 사용한다.

### 4. 서버 실행

```bash
cd server
./gradlew bootRun
```

최초 실행 시 Flyway가 `V1__init.sql`로 테이블을 만든다.

---

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/scope.md`](docs/scope.md) | MVP·확장 구분, 주차별 일정, 포기 순서 |
| [`docs/location-policy.md`](docs/location-policy.md) | 반경 정책, 위치 갱신 규칙, `/config` 스펙, 반경 실험 계획 |

## 설계 결정

| | 결정 |
|---|---|
| **D1** | 반경의 중심은 사용자다 — 격자로 나누지 않는다 |
| **D2** | 반경은 밀도에 따라 바뀌지 않는다 — 기본 300m, 실험으로 확정 |
| **D3** | 메시지는 만료되지 않는다 |
| **D4** | 이름은 없고, 구간 표식만 있다 |
| **D5** | 실시간 전달과 영구 저장을 동시에 수행한다 |
| **D6** | 관계 대신 반응으로 재방문을 만든다 |
