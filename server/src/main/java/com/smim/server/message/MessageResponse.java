package com.smim.server.message;

import java.time.Instant;

/**
 * GET /messages 의 한 항목. POST /messages 응답과 WebSocket 페이로드(#12)도 이 형태다. 근거: docs/api.md
 *
 * author_key · 좌표는 넣지 않는다 (api.md 1-1 · 1-4).
 */
public record MessageResponse(long id, String content, int marker, boolean isMine, Instant createdAt,
		QuotedMessage replyTo) {

	/** 인용 표시에 필요한 만큼만. 원글이 이 사용자에게 보이지 않으면 content 는 null */
	public record QuotedMessage(long id, String content, Instant createdAt) {
	}
}
