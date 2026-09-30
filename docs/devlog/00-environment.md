# 00. 개발 환경 구축에서 막힌 것들

> 2026-09 · 2주차
> 서버·DB·앱이 한 번에 뜨기까지 세 번 막혔다. 전부 코드 문제가 아니었다.

---

## 1. 공식 PostGIS 이미지는 arm64를 지원하지 않는다

개발 환경은 Apple Silicon, 배포 대상은 Oracle Cloud Ampere — **둘 다 arm64**다.
아키텍처가 같으니 크로스 빌드 걱정이 없다고 생각했는데, 이미지 매니페스트를 직접 열어보니 달랐다.

```bash
docker manifest inspect postgis/postgis:17-3.5 | grep architecture
```

| 이미지 | amd64 | arm64 |
|---|:---:|:---:|
| `postgis/postgis:17-3.5` (공식) | ✅ | **❌** |
| `imresamu/postgis:17-3.5` | ✅ | ✅ |
| `redis:7.4-alpine` | ✅ | ✅ |

공식 이미지는 amd64 빌드만 있다. 맥에서는 에뮬레이션으로 느리게 돌아가고,
**Oracle ARM 서버에서는 아예 뜨지 않는다.** 배포 주차에 가서야 발견했으면 일정이 통째로 밀렸을 것이다.

`imresamu/postgis` 는 공식 docker-postgis 저장소 메인테이너가 관리하는 멀티아키텍처 빌드다.
이걸로 바꾸고 동작을 확인했다.

```
POSTGIS="3.5.3" PGSQL="170" GEOS="3.11.1" PROJ="9.1.1"
```

**배운 것** — "아키텍처가 같으니 괜찮다"는 추측이었다. 이미지가 그 아키텍처를 빌드하는지는
별개 문제이고, `docker manifest inspect` 로 1초면 확인된다.

---

## 2. Flyway가 에러 없이 조용히 건너뛰었다

서버가 정상 기동하고 로그도 깨끗한데 **테이블이 하나도 없었다.**

```
Tomcat started on port 8082 (http)
Started ServerApplication in 2.799 seconds
```

Flyway 관련 로그가 **한 줄도 없다.** 실패한 게 아니라 아예 실행되지 않은 것이다.

```sql
smim=# \dt
 public | spatial_ref_sys | table | smim     ← PostGIS 내장 테이블뿐
```

의존성도 설정도 맞아 보였다.

```gradle
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

```yaml
spring.flyway.enabled: true
spring.flyway.locations: classpath:db/migration
```

원인은 **Spring Boot 4에서 자동 설정이 모듈로 분리된 것**이었다.
Boot 3까지는 `flyway-core` 가 클래스패스에 있으면 `spring-boot-autoconfigure` 가 알아서 실행했지만,
Boot 4는 `spring-boot-flyway` 모듈이 따로 필요하다.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-flyway'
```

```
+--- org.springframework.boot:spring-boot-starter-flyway -> 4.1.1
|    +--- org.springframework.boot:spring-boot-flyway:4.1.1     ← 빠져 있던 것
|    |    \--- org.flywaydb:flyway-core:12.4.0
```

바꾸자 바로 돌았다.

```
Successfully validated 2 migrations
Migrating schema "public" to version "1 - init"
Migrating schema "public" to version "2 - active session"
Successfully applied 2 migrations
```

> `flyway-database-postgresql` 은 Boot 4에서도 여전히 별도로 필요하다.
> 이게 빠지면 반대로 `Unsupported Database: PostgreSQL` 로 **기동 자체가 실패**한다.
> 하나는 조용히 건너뛰고 하나는 요란하게 죽는다.

**배운 것** — 가장 무서운 실패는 에러를 내지 않는 실패다. 마이그레이션은 로그를 믿지 말고
`\dt` 로 테이블을 직접 확인해야 한다. README의 실행 절차에 그 단계를 넣었다.

---

## 3. 포트가 세 번 겹쳤다

로컬에 다른 프로젝트가 돌고 있어서 연달아 막혔다.

```
Bind for 0.0.0.0:5432 failed: port is already allocated
Web server failed to start. Port 8080 was already in use.
Web server failed to start. Port 8081 was already in use.
```

| 포트 | 점유하고 있던 것 |
|---|---|
| 5432 | 다른 프로젝트의 postgres 컨테이너 |
| 6379 | 다른 프로젝트의 redis 컨테이너 |
| 8080 | 10일째 떠 있던 다른 Spring 앱 |
| 8081 | **Expo Metro 번들러** |

8081이 특히 문제였다. 처음에 8080을 피해 8081로 옮겼는데, 그건 **앱 개발 중 항상 쓰는 포트**였다.
서버와 앱을 동시에 띄울 수 없게 되는 자리라 다시 옮겼다.

포트를 코드에서 빼고 `.env` 로 모았다.

```bash
POSTGRES_PORT=5433
REDIS_PORT=6380
SERVER_PORT=8082
```

compose와 Spring이 같은 파일을 읽으므로 앞으로 겹치면 한 줄만 고치면 된다.
컨테이너 내부 포트는 그대로라 배포 환경에는 영향이 없다.

```yaml
ports:
  - "127.0.0.1:${POSTGRES_PORT:-5432}:5432"
```

`127.0.0.1` 로 묶은 것도 의도적이다. 이 파일을 서버에 그대로 올려도 **DB가 인터넷에 노출되지 않는다.**

**배운 것** — 포트는 코드가 아니라 환경이다. 처음부터 환경변수로 빼뒀으면 세 번 고칠 일을
한 번에 끝냈을 것이다.

---

## 정리

세 문제 모두 **코드를 짜기 전에 막힌 것**이고, 전부 "확인해 봤으면 바로 알았을" 것들이었다.

- 이미지가 내 아키텍처를 지원하는지 → `docker manifest inspect`
- 마이그레이션이 실제로 돌았는지 → `\dt`
- 포트가 비어 있는지 → `lsof -nP -iTCP:<port> -sTCP:LISTEN`

환경 구축은 "되면 넘어가는" 단계로 취급하기 쉬운데, 여기서 만든 가정이 틀리면
훨씬 나중에 훨씬 비싸게 드러난다.
