package com.smim.server.live;

import com.smim.server.common.DeviceKeys;
import com.smim.server.common.GeoPoint;
import com.smim.server.presence.PresenceStore;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP CONNECT 에서 인증하고 위치를 등록한다. 근거: docs/api.md WebSocket /ws
 *
 * CONNECT 헤더 — X-Device-Key, session-id (POST /presence 와 같은 값), lat · lon (있으면 바로 등록)
 *
 * 클라이언트는 받기만 한다. 구독은 /user/queue/messages 하나만 허용하고, SEND 는 막는다 — 글쓰기는 REST 로.
 */
@Component
class StompAuthInterceptor implements ChannelInterceptor {

	static final String SESSION_HEADER = "session-id";

	static final String SUBSCRIPTION = "/user/queue/messages";

	private final LiveSessions sessions;

	private final PresenceStore presence;

	StompAuthInterceptor(LiveSessions sessions, PresenceStore presence) {
		this.sessions = sessions;
		this.presence = presence;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (acc == null || acc.getCommand() == null) {
			return message;
		}
		switch (acc.getCommand()) {
			case CONNECT, STOMP -> connect(acc);
			case SUBSCRIBE -> {
				if (!SUBSCRIPTION.equals(acc.getDestination())) {
					throw new MessageDeliveryException("구독할 수 없는 경로입니다.");
				}
			}
			case SEND -> throw new MessageDeliveryException("글쓰기는 POST /messages 로 합니다.");
			default -> {
			}
		}
		return message;
	}

	private void connect(StompHeaderAccessor acc) {
		// 형식이 틀리면 ApiException(INVALID_DEVICE_KEY) — 클라이언트는 ERROR 프레임을 받고 연결이 닫힌다
		String authorKey = DeviceKeys.toAuthorKey(acc.getFirstNativeHeader(DeviceKeys.HEADER));
		String sessionId = acc.getFirstNativeHeader(SESSION_HEADER);
		if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64) {
			throw new MessageDeliveryException("session-id 가 필요합니다.");
		}

		LiveSession s = new LiveSession(sessionId, authorKey, acc.getSessionId());
		if (!sessions.register(s)) {
			throw new MessageDeliveryException("이미 사용 중인 session-id 입니다.");
		}
		acc.setUser(s);

		// 재연결 직후부터 받을 수 있게, 다음 하트비트(최대 45초)를 기다리지 않고 바로 등록한다
		GeoPoint at = point(acc);
		if (at != null) {
			presence.upsert(sessionId, authorKey, at);
		}
	}

	private static GeoPoint point(StompHeaderAccessor acc) {
		try {
			double lat = Double.parseDouble(acc.getFirstNativeHeader("lat"));
			double lon = Double.parseDouble(acc.getFirstNativeHeader("lon"));
			return Math.abs(lat) <= 90 && Math.abs(lon) <= 180 ? new GeoPoint(lat, lon) : null;
		}
		catch (NullPointerException | NumberFormatException e) {
			return null;
		}
	}
}
