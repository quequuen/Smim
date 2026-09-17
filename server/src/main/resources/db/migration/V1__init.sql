-- 스밈 초기 스키마 (MVP)
-- 설계 근거: 기획서 D1~D6, docs/location-policy.md, docs/scope.md

CREATE EXTENSION IF NOT EXISTS postgis;


-- ── 메시지 ────────────────────────────────────────────────────
-- 좌표에 영구히 남는다 (D3). TTL 없음.
CREATE TABLE message (
    id           BIGSERIAL              PRIMARY KEY,
    content      VARCHAR(500)           NOT NULL,
    location     GEOGRAPHY(POINT, 4326) NOT NULL,

    -- 기기 익명 키 해시. 삭제·차단·신고 처리에만 쓰며 절대 API 응답에 포함하지 않는다 (D4)
    author_key   CHAR(64)               NOT NULL,

    -- 구간 표식 0~23 (도형 6 × 색 4). 작성 시점에 확정해 저장한다 (D4)
    marker       SMALLINT               NOT NULL CHECK (marker BETWEEN 0 AND 23),

    -- 인용 답글
    reply_to_id  BIGINT                 REFERENCES message (id),

    status       VARCHAR(16)            NOT NULL DEFAULT 'visible'
                 CHECK (status IN ('visible', 'hidden', 'deleted')),
    created_at   TIMESTAMPTZ            NOT NULL DEFAULT now()
);

-- 반경 조회 (ST_DWithin 이 이 인덱스를 직접 사용한다)
CREATE INDEX idx_message_location   ON message USING GIST (location);
-- 시간순 스크롤 · 커서 페이지네이션
CREATE INDEX idx_message_created_at ON message (created_at DESC);
-- 본인 글 조회 · 삭제
CREATE INDEX idx_message_author     ON message (author_key);
-- 인앱 답글 표시 ("내 글에 달린 답글")
CREATE INDEX idx_message_reply_to   ON message (reply_to_id) WHERE reply_to_id IS NOT NULL;


-- ── 신고 ──────────────────────────────────────────────────────
CREATE TABLE report (
    id            BIGSERIAL    PRIMARY KEY,
    message_id    BIGINT       NOT NULL REFERENCES message (id),
    reporter_key  CHAR(64)     NOT NULL,
    reason        VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 같은 사람이 같은 글을 여러 번 신고해 자동 블라인드를 유발하는 것을 막는다
    UNIQUE (message_id, reporter_key)
);

CREATE INDEX idx_report_message ON report (message_id);


-- ── 차단 ──────────────────────────────────────────────────────
-- 이름이 없으므로 "이 작성자 글 그만 보기"를 내부 키로 처리한다
CREATE TABLE user_block (
    blocker_key         CHAR(64)     NOT NULL,
    blocked_author_key  CHAR(64)     NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_key, blocked_author_key)
);


-- ── 작성 금지 구역 ─────────────────────────────────────────────
-- MVP에서는 데이터가 비어 있어도 된다. 문제 발생 시 즉시 차단할 수단을 확보하는 것이 목적.
CREATE TABLE banned_area (
    id          BIGSERIAL              PRIMARY KEY,
    center      GEOGRAPHY(POINT, 4326) NOT NULL,
    radius_m    INTEGER                NOT NULL CHECK (radius_m > 0),
    reason      VARCHAR(64)            NOT NULL,

    -- manual = 관리자 수동 등록, auto = 신고 누적으로 자동 등록
    source      VARCHAR(16)            NOT NULL DEFAULT 'manual'
                CHECK (source IN ('manual', 'auto')),
    created_at  TIMESTAMPTZ            NOT NULL DEFAULT now()
);

CREATE INDEX idx_banned_area_center ON banned_area USING GIST (center);
