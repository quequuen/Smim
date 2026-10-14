package com.smim.server.block;

import com.smim.server.common.ApiException;
import com.smim.server.common.ErrorCode;
import com.smim.server.message.MessageRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 차단 — "이 작성자 글 그만 보기". 근거: docs/api.md POST · GET · DELETE /blocks
 *
 * 쿼리가 세 개뿐이라 저장소를 따로 두지 않는다.
 */
@Service
public class BlockService {

	private final NamedParameterJdbcTemplate jdbc;

	private final MessageRepository messages;

	public BlockService(NamedParameterJdbcTemplate jdbc, MessageRepository messages) {
		this.jdbc = jdbc;
		this.messages = messages;
	}

	/** 이미 차단한 작성자면 아무것도 하지 않는다 — 앱 입장에서는 같은 결과다 */
	public void block(String me, long messageId) {
		String author = messages.findVisibleAuthor(messageId)
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
		if (author.equals(me)) {
			// 나를 차단하면 내 글이 내게서 사라진다. 앱 메뉴에도 없는 동작이다
			throw new ApiException(ErrorCode.INVALID_REQUEST);
		}
		jdbc.update("""
				INSERT INTO user_block (blocker_key, blocked_author_key, message_id) VALUES (:me, :author, :msg)
				ON CONFLICT (blocker_key, blocked_author_key) DO NOTHING
				""", new MapSqlParameterSource().addValue("me", me).addValue("author", author).addValue("msg", messageId));
	}

	public List<BlockResponse> list(String me) {
		return jdbc.query("""
				SELECT b.id, CASE WHEN m.status = 'visible' THEN m.content END AS message_content, b.created_at
				FROM user_block b
				JOIN message m ON m.id = b.message_id
				WHERE b.blocker_key = :me
				ORDER BY b.created_at DESC, b.id DESC
				""", new MapSqlParameterSource("me", me),
				(rs, i) -> new BlockResponse(rs.getLong("id"), rs.getString("message_content"),
						rs.getObject("created_at", OffsetDateTime.class).toInstant()));
	}

	/** 남의 차단 항목이면 없는 것과 같은 404 */
	public void unblock(String me, long blockId) {
		int n = jdbc.update("DELETE FROM user_block WHERE id = :id AND blocker_key = :me",
				new MapSqlParameterSource().addValue("id", blockId).addValue("me", me));
		if (n == 0) {
			throw new ApiException(ErrorCode.NOT_FOUND);
		}
	}
}
