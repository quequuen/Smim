#!/usr/bin/env bash
# 서버에서 실행된다. GitHub Actions 가 app.jar.new 를 올린 뒤 호출한다.
#
# 기동에 실패하면 직전 jar 로 되돌린다 — 심사 중인 Asker 와 같은 박스라
# "배포했는데 안 뜬 상태"로 방치하지 않는 것이 중요하다.
set -euo pipefail

APP_DIR=/home/ubuntu/smim
NEW="$APP_DIR/app.jar.new"
CUR="$APP_DIR/app.jar"
PREV="$APP_DIR/app.jar.prev"

# 기기 키 없이 부르면 401 이 정상이다 — 그 자체가 앱이 응답한다는 뜻
HEALTH_URL=http://127.0.0.1:8082/config
HEALTH_CODE=401
TRIES=30

healthy() {
  for _ in $(seq 1 "$TRIES"); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" || true)" = "$HEALTH_CODE" ]; then
      return 0
    fi
    sleep 2
  done
  return 1
}

[ -f "$NEW" ] || { echo "app.jar.new 가 없다"; exit 1; }

if [ -f "$CUR" ]; then
  cp -f "$CUR" "$PREV"
  echo "직전 jar 를 app.jar.prev 로 보관"
fi

mv -f "$NEW" "$CUR"
sudo systemctl restart smim

if healthy; then
  echo "배포 성공"
  exit 0
fi

echo "기동 실패 — 되돌린다"
sudo journalctl -u smim -n 30 --no-pager || true

if [ -f "$PREV" ]; then
  mv -f "$PREV" "$CUR"
  sudo systemctl restart smim
  if healthy; then
    echo "롤백 완료 — 직전 버전으로 돌아감"
  else
    echo "롤백 후에도 비정상이다. 손으로 봐야 한다"
  fi
fi
exit 1
