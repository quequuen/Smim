-- 컨테이너를 처음 만들 때 한 번만 실행된다 (데이터 볼륨이 비어 있을 때).
-- 확장 기능은 슈퍼유저 권한이 필요하므로 여기서 켠다.
-- 테이블은 여기서 만들지 않는다 — Flyway(server/src/main/resources/db/migration)가 관리한다.

CREATE EXTENSION IF NOT EXISTS postgis;
