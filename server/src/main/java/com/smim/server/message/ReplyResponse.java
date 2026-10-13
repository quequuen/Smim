package com.smim.server.message;

import java.time.Instant;

/** GET /replies 의 한 항목 — 내 글에 달린 답글 (D6) */
public record ReplyResponse(long id, String content, int marker, Instant createdAt, MyMessage myMessage) {

	public record MyMessage(long id, String content) {
	}
}
