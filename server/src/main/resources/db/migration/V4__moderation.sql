-- 모더레이션 (#16)
--
-- 새 테이블은 없다. 필요한 것이 V1 에 이미 있다:
--   · 전체 공개 차단   message.status = 'hidden'  (CHECK 에 포함돼 있음)
--   · 신고자 본인 숨김  report 행 + MessageRepository.visibleTo()
--   · 금지 구역        banned_area (source = 'manual' | 'auto')
--
-- 여기서는 글쓰기마다 도는 두 질의를 위한 인덱스만 추가한다.

-- 도배 제한 — "이 기기가 최근 N분 안에 몇 개 썼나".
-- idx_message_author 는 author_key 단독이라 해당 기기의 전체 글을 훑는다.
-- 글이 쌓일수록 느려지고, 그 비용을 모든 글쓰기가 치른다.
CREATE INDEX idx_message_author_recent ON message (author_key, created_at DESC);

-- 자동 구역 차단 — "이 반경 안에 가려진 글이 최근 24시간 동안 몇 건인가".
-- 가려진 글은 전체의 극히 일부이므로 부분 인덱스로 충분하다.
CREATE INDEX idx_message_hidden ON message USING GIST (location)
    WHERE status = 'hidden';
