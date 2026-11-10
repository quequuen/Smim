# 배포

`api.smim.app` 은 **Asker 와 같은 Oracle Cloud 인스턴스**에서 돈다.
무료 ARM 한도(4 OCPU / 24 GB)를 Asker 가 전부 점유하고 있어 전용 인스턴스를 만들 수 없었다.
한 박스를 나눠 쓰면 새 용량을 요청할 일이 없어 그 문제가 사라진다.

| | Asker | 스밈 |
|---|---|---|
| 앱 포트 | 8080 | **8082** (`127.0.0.1` 바인딩) |
| DB | PostgreSQL 16 (OS 직접) `127.0.0.1:5432` | **PG17 컨테이너** `127.0.0.1:5433` |
| 디렉터리 | `~/asker-backend` | **`~/smim`** |
| nginx | `sites-available/default` | **`sites-available/smim.conf`** |

## 서버에 놓이는 것

```
~/smim/
  docker-compose.yml      저장소 것 그대로
  db/init/                PostGIS 확장
  .env                    커밋하지 않는다 (권한 600)
  app.jar                 bootJar 산출물
/etc/nginx/sites-available/smim.conf   ← nginx/smim.conf
/etc/systemd/system/smim.service       ← deploy/smim.service
```

## 올리기

```bash
cd server && ./gradlew bootJar
scp build/libs/server-0.0.1-SNAPSHOT.jar asker:~/smim/app.jar
ssh asker 'sudo systemctl restart smim'
```

`nginx` 는 **`reload` 만 쓴다.** `restart` 하면 같은 박스의 Asker 연결이 끊긴다.

## 확인

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://api.smim.app/config          # 401
curl -i --http1.1 -H "Connection: Upgrade" -H "Upgrade: websocket" \
     -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: $(openssl rand -base64 16)" \
     https://api.smim.app/ws | head -1                                        # 101
```

REST 가 된다고 WebSocket 이 되는 게 아니므로 **둘 다** 본다.
`101` 이 아니면 `nginx/smim.conf` 의 `location /ws` 에서 `Upgrade` · `Connection` 전달이 빠진 것이다.
