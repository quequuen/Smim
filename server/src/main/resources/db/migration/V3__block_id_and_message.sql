-- 차단 목록 화면 (docs/api.md GET /blocks · DELETE /blocks/{id})
--
-- 작성자를 보여줄 수 없으므로(D4) 차단할 때 지목한 글을 함께 보여준다 → message_id
-- 해제할 때 author_key 를 주고받을 수 없으므로 행을 가리킬 대리 키가 필요하다 → id
--
-- 배포 전이고 행이 없으므로 message_id 를 NOT NULL 로 바로 추가한다.

ALTER TABLE user_block DROP CONSTRAINT user_block_pkey;

ALTER TABLE user_block ADD COLUMN id         BIGSERIAL PRIMARY KEY;
ALTER TABLE user_block ADD COLUMN message_id BIGINT    NOT NULL REFERENCES message (id);

-- 같은 작성자를 두 번 차단하지 않는다. 목록 조회(blocker_key = ?)도 이 인덱스를 쓴다
ALTER TABLE user_block ADD CONSTRAINT user_block_blocker_author UNIQUE (blocker_key, blocked_author_key);
