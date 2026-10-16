package com.smim.server.live;

import java.security.Principal;

/**
 * WebSocket 연결 하나. getName() 이 presence 의 sessionId 라서 convertAndSendToUser(sessionId, ...) 로 바로 보낼 수 있다.
 *
 * authorKey 는 서버 안에서만 쓴다 — 수신자별 필터(차단 · 그룹 반경)에 필요하다.
 */
record LiveSession(String sessionId, String authorKey, String wsSessionId) implements Principal {

	@Override
	public String getName() {
		return sessionId;
	}
}
