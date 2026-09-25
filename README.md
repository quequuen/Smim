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
| 서버 | Spring Boot 4.1.1 · Java 17 |
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
| Project | Gradle - Groovy |
| Language | Java |
| Spring Boot | 4.1.1 (스냅샷·마일스톤이 아닌 최신 정식) |
| Group | `com.smim` |
| Artifact | `server` |
| Package name | `com.smim.server` |
| Packaging | Jar |
| **Configuration** | **YAML** |
| Java | 17 |

> **Configuration 을 YAML 로 고른다.** 저장소에 이미 `application.yml` 이 있어
> Properties 로 만들면 설정이 둘로 갈린다.
>
> **Java 17 로 충분하다.** 21 의 가상 스레드는 동시 요청이 수천 건일 때 의미가 있고
> 이 프로젝트 규모에서는 측정에 차이가 나지 않는다. 나중에 올리려면
> `build.gradle` 의 `toolchain` 한 줄만 바꾸면 된다.

**의존성**

- Spring Web
- WebSocket
- Spring Data JPA
- PostgreSQL Driver
- Spring Data Redis
- Flyway Migration
- Validation

생성 후 `build.gradle`의 의존성을 아래로 맞춘다.

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'

    implementation 'org.hibernate.orm:hibernate-spatial'        // GEOGRAPHY 매핑
    implementation 'org.flywaydb:flyway-database-postgresql'    // PostgreSQL 방언

    runtimeOnly 'org.postgresql:postgresql'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

> **`flyway-core` 가 아니라 `spring-boot-starter-flyway` 를 쓴다.**
> Boot 4 는 자동 설정이 모듈로 분리되어, `flyway-core` 만 있으면 라이브러리는 클래스패스에
> 있지만 Spring 이 실행하지 않는다. **에러 없이 조용히 건너뛰므로** 테이블을 확인하지 않으면
> 모르고 지나간다.
>
> `flyway-database-postgresql` 도 빠뜨리면 `Unsupported Database: PostgreSQL` 로 기동에 실패한다.

압축을 풀 때 **기존 파일을 덮어쓰지 않도록** 주의한다. 아래 셋은 저장소의 것을 유지한다.

```
server/src/main/resources/application.yml
server/src/main/resources/db/migration/V1__init.sql
server/src/main/resources/db/migration/V2__active_session.sql
```

해제 후 `git status` 로 이 파일들이 수정되지 않았는지 확인한다.

### 4. 서버 실행

```bash
cd server
./gradlew bootRun
```

최초 실행 시 Flyway가 `V1`·`V2`를 적용한다. **테이블이 실제로 생겼는지 확인한다.**

```bash
docker compose exec postgres psql -U smim -d smim -P pager=off -c "\dt"
```

`message` `report` `user_block` `banned_area` `active_session` 과
`flyway_schema_history` 가 보이면 정상이다.

### 5. 앱 실행 (실기기 · Expo Go)

```bash
cd app
cp .env.example .env    # EXPO_PUBLIC_API_URL 에 맥의 LAN IP 를 넣는다
npm install
npx expo start
```

> **서버 주소에 `localhost`를 쓰면 안 된다.** 폰 입장에서 localhost는 폰 자신이다.
> 맥의 LAN IP를 쓴다 — `ipconfig getifaddr en0`. 폰과 맥이 같은 Wi-Fi에 있어야 한다.
> `.env`를 바꾼 뒤에는 `npx expo start -c` 로 캐시를 비워야 반영된다.

`EXPO_PUBLIC_API_URL`을 비워 두면 서버 없이 mock으로 돈다.

폰에서 위치를 허용하면 앱이 `heartbeatSec`(45초)마다 `POST /presence`로 좌표를 보낸다.
개발 빌드에서는 헤더의 "이 근처" 아래에 `server · ok 14:02:11` 처럼 마지막 응답 시각이 뜬다.
서버 쪽에서 받은 좌표를 보려면 DEBUG 로그를 켠다.

```bash
./gradlew bootRun --args='--logging.level.com.smim=debug'
```

---

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/tracks.md`](docs/tracks.md) | A(MVP) · B(위치 계층 선택) · +A(확장) 구분과 진행 순서 |
| [`docs/scope.md`](docs/scope.md) | MVP·확장 상세, 주차별 일정, 포기 순서 |
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
