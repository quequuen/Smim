-- 접속자 현재 위치 (트랙 B · docs/tracks.md)
--
-- "지금 접속해 있는 사람"의 위치만 담는다. 사라져도 되는 데이터이며,
-- 메시지·신고·차단 같은 영구 데이터와는 성격이 다르다.
-- Redis GEO로 바꾸더라도 이 테이블만 쓰지 않게 되고 나머지 코드는 그대로다.

CREATE TABLE active_session (
    session_id   VARCHAR(64)            PRIMARY KEY,
    author_key   CHAR(64)               NOT NULL,
    location     GEOGRAPHY(POINT, 4326) NOT NULL,
    updated_at   TIMESTAMPTZ            NOT NULL DEFAULT now()
)
-- 위치 갱신이 잦은 테이블이므로 페이지에 여유를 둔다
WITH (fillfactor = 70);

-- 전달 대상 조회 (ST_DWithin 이 직접 사용한다)
CREATE INDEX idx_active_session_loc ON active_session USING GIST (location);

-- updated_at 에는 인덱스를 걸지 않는다.
-- 하트비트가 이 컬럼만 갱신할 때 HOT update 가 가능해야 하기 때문이다.
