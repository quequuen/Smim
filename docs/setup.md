# 개발 환경 구축

README의 [실행 방법](../README.md#실행-방법)이 안 되거나, 포트·서버 주소를 바꿔야 할 때 본다.

---

## 1. 환경변수

```bash
cp .env.example .env
```

`.env`는 **docker compose와 Spring Boot가 함께 읽는다.** 비밀번호를 두 군데 적을 필요가 없다.

```bash
POSTGRES_PORT=5433
POSTGRES_DB=smim
POSTGRES_USER=smim
POSTGRES_PASSWORD=...

REDIS_PORT=6380
REDIS_PASSWORD=...

SERVER_PORT=8082
```

### 포트가 겹칠 때

기본값을 5432·6379·8080이 아닌 값으로 잡아 둔 이유가 있다.

| 포트 | 흔히 쓰는 것 | 스밈 |
|---|---|---|
| 5432 | 다른 프로젝트의 postgres | **5433** |
| 6379 | 다른 프로젝트의 redis | **6380** |
| 8080 | 다른 Spring 앱 | — |
| 8081 | **Expo Metro 번들러** | — |
| | | **8082** |

**8081은 피해야 한다.** 앱을 개발하는 동안 Metro가 항상 쓰는 포트라, 서버를 여기 두면
앱과 서버를 동시에 띄울 수 없다.

또 겹치면 `.env`만 고치면 된다. 컨테이너 내부 포트는 그대로라 배포에는 영향이 없다.

```bash
lsof -nP -iTCP:5433 -sTCP:LISTEN     # 누가 쓰는지 확인
```

> 컨테이너를 이미 만든 뒤 포트를 바꾸면 재생성해야 반영된다.
> `docker compose up -d --force-recreate postgres`

---

## 2. 인프라

```bash
docker compose up -d postgres
```

Redis는 8주차(트랙 A)까지 필요 없으므로 `postgres`만 띄워도 된다.

PostGIS가 정상인지 확인한다.

```bash
docker compose exec postgres psql -U smim -d smim -P pager=off -c "SELECT postgis_full_version();"
```

```
POSTGIS="3.5.3" PGSQL="170" GEOS="3.11.1" PROJ="9.1.1"
```

> **공식 `postgis/postgis` 이미지는 arm64를 지원하지 않는다.** Apple Silicon에서는 에뮬레이션으로
> 느리게 돌고 ARM 서버에서는 아예 뜨지 않는다. 멀티아키텍처 빌드인 `imresamu/postgis`를 쓴다.
> → [devlog 00](devlog/00-environment.md)

---

## 3. 서버

```bash
cd server && ./gradlew bootRun
```

기동 후 **테이블이 실제로 생겼는지 확인한다.**

```bash
docker compose exec postgres psql -U smim -d smim -P pager=off -c "\dt"
```

```
message · report · user_block · banned_area · active_session · flyway_schema_history
```

> 로그가 깨끗한데 테이블이 없다면 Flyway가 실행되지 않은 것이다. Boot 4는 자동 설정이
> 모듈로 분리되어 `flyway-core`만으로는 돌지 않고 `spring-boot-starter-flyway`가 필요하다.
> **에러 없이 조용히 건너뛰므로** 로그만 보면 알 수 없다. → [devlog 00](devlog/00-environment.md)

### 받은 좌표 확인

```bash
./gradlew bootRun --args='--logging.level.com.smim=debug'
```

좌표는 민감 정보라 DEBUG 레벨에만 남기고, 기기 키는 앞 8자만 찍는다.

---

## 4. 앱

```bash
cd app
cp .env.example .env
npm install
npx expo start
```

QR을 **Expo Go**로 스캔한다.

### 서버 주소 — `localhost`를 쓰면 안 된다

폰 입장에서 `localhost`는 폰 자신이다. Mac의 LAN IP를 넣어야 한다.

```bash
ipconfig getifaddr en0
```

```bash
# app/.env
EXPO_PUBLIC_API_URL=http://192.168.0.5:8082
```

- 폰과 Mac이 **같은 Wi-Fi**에 있어야 한다
- `.env`를 바꾼 뒤에는 `npx expo start -c`로 캐시를 비워야 반영된다
- `EXPO_PUBLIC_API_URL`을 **비워 두면 서버 없이 mock으로 돈다**

서버 배포 후에는 도메인을 쓰므로 이 문제가 사라진다.

### 동작 확인

위치를 허용하면 앱이 `heartbeatSec`(45초)마다 `POST /presence`로 좌표를 보낸다.
개발 빌드에서는 헤더 "이 근처" 아래에 `server · ok 14:02:11` 처럼 마지막 응답 시각이 표시된다.

---

## 5. DB 직접 보기 (DBeaver)

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | **`5433`** |
| Database | `smim` |
| Username | `smim` |
| Password | `.env`의 `POSTGRES_PASSWORD` |

`location`은 `geography` 타입이라 그냥 보면 바이너리로 나온다. 좌표로 보려면 캐스팅한다.

```sql
SELECT id, content,
       ST_Y(location::geometry) AS lat,
       ST_X(location::geometry) AS lon,
       location::geometry AS geom,
       created_at
FROM message
ORDER BY created_at DESC;
```

`geom` 셀을 선택하면 **Spatial 탭**에서 지도 위 위치를 확인할 수 있다.
좌표가 뒤바뀌었는지(`ST_MakePoint`는 경도가 먼저다) 눈으로 잡을 때 유용하다.

---

## 6. 서버 프로젝트를 새로 생성할 경우

이미 `server/`가 있으므로 보통은 필요 없다. 처음부터 다시 만든다면
[start.spring.io](https://start.spring.io)에서 아래 설정을 쓴다.

| 항목 | 값 |
|---|---|
| Project | Gradle - Groovy |
| Spring Boot | 4.1.1 |
| Group / Artifact | `com.smim` / `server` |
| **Configuration** | **YAML** |
| Java | 17 |

의존성은 Web · WebSocket · Data JPA · PostgreSQL Driver · Data Redis · Flyway · Validation.
생성 후 `build.gradle`에 아래를 추가한다.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-flyway'  // flyway-core 아님
implementation 'org.hibernate.orm:hibernate-spatial'                  // GEOGRAPHY 매핑
implementation 'org.flywaydb:flyway-database-postgresql'              // PostgreSQL 방언
```

압축을 풀 때 기존 `application.yml`과 `db/migration/*.sql`을 **덮어쓰지 않도록** 주의한다.
