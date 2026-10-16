package com.smim.server.live;

import com.smim.server.presence.PresenceStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 이 서버에 붙어 있는 연결. PresenceStore 는 "어디에 있는가", 이것은 "이 서버로 보낼 수 있는가" 다.
 *
 * 서버가 여러 대가 되면 이 맵만으로는 부족하다 — 서버 간 릴레이가 필요하다 (tracks.md 4-4).
 */
@Component
public class LiveSessions {

	private final Map<String, LiveSession> bySessionId = new ConcurrentHashMap<>();

	private final PresenceStore presence;

	public LiveSessions(PresenceStore presence) {
		this.presence = presence;
	}

	/** @return false 면 이 sessionId 를 다른 기기가 쓰고 있다 */
	boolean register(LiveSession s) {
		// 같은 기기의 재연결은 새 연결로 갈아 끼운다. 다른 기기면 그대로 둔다
		LiveSession now = bySessionId.compute(s.sessionId(),
				(id, prev) -> prev == null || prev.authorKey().equals(s.authorKey()) ? s : prev);
		return now == s;
	}

	public Optional<LiveSession> find(String sessionId) {
		return Optional.ofNullable(bySessionId.get(sessionId));
	}

	/**
	 * 만료 처리 ① — 연결이 끊기면 위치도 바로 지운다 (tracks.md 2장).
	 * 재연결이 먼저 도착했으면 옛 연결의 끊김이 새 연결을 지우지 않도록 wsSessionId 를 대조한다.
	 */
	@EventListener
	void onDisconnect(SessionDisconnectEvent e) {
		if (!(e.getUser() instanceof LiveSession s)) {
			return;
		}
		if (bySessionId.remove(s.sessionId(), new LiveSession(s.sessionId(), s.authorKey(), e.getSessionId()))) {
			presence.remove(s.sessionId());
		}
	}
}
