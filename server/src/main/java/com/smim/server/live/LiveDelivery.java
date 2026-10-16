package com.smim.server.live;

import com.smim.server.config.SmimProperties;
import com.smim.server.message.MessageCreated;
import com.smim.server.message.MessageRepository;
import com.smim.server.message.MessageResponse;
import com.smim.server.presence.PresenceStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 반경 기반 선택적 전달 (D5). 근거: docs/tracks.md 1장 ②
 *
 * 저장이 커밋된 뒤에 전달 대상을 고른다 — PresenceStore 는 트랜잭션에 참여하지 않으므로(Redis), 커밋 전에 보내면
 * 롤백된 글이 나갈 수 있고, 구현을 바꿨을 때 동작이 달라진다.
 *
 * 수신자마다 GET /messages 와 같은 결과가 되게 거른다.
 * - 수신자의 실험 그룹 반경 안일 때만 — 100m 그룹에게 400m 밖의 글이 오면 실험이 섞인다
 * - 작성자를 차단한 사람에게는 보내지 않는다
 * - 인용한 원글을 신고 · 차단한 사람에게는 replyTo.content 를 비운다
 * - 작성자 본인에게는 보내지 않는다 — POST 응답으로 이미 붙였다
 */
@Component
class LiveDelivery {

	private static final Logger log = LoggerFactory.getLogger(LiveDelivery.class);

	static final String DESTINATION = "/queue/messages";

	private final PresenceStore presence;

	private final LiveSessions sessions;

	private final MessageRepository messages;

	private final SmimProperties props;

	private final SimpMessagingTemplate broker;

	LiveDelivery(PresenceStore presence, LiveSessions sessions, MessageRepository messages, SmimProperties props,
			SimpMessagingTemplate broker) {
		this.presence = presence;
		this.sessions = sessions;
		this.messages = messages;
		this.props = props;
		this.broker = broker;
	}

	@TransactionalEventListener
	void onCreated(MessageCreated e) {
		// 커밋은 이미 끝났다. 전달이 실패해도 글쓰기 응답을 실패로 만들지 않는다 — 재연결 시 GET 으로 보충된다
		try {
			deliver(e);
		}
		catch (RuntimeException ex) {
			log.warn("live delivery failed for message {}", e.message().id(), ex);
		}
	}

	private void deliver(MessageCreated e) {
		List<LiveSession> targets = new ArrayList<>();
		for (SmimProperties.Group g : props.experiment().groups()) {
			for (String sessionId : presence.findSessionsWithin(e.at(), g.radiusM())) {
				sessions.find(sessionId)
					.filter(s -> !s.authorKey().equals(e.authorKey()))
					.filter(s -> props.experiment().groupOf(s.authorKey()).equals(g))
					.ifPresent(targets::add);
			}
		}
		if (targets.isEmpty()) {
			return;
		}

		List<String> viewers = targets.stream().map(LiveSession::authorKey).distinct().toList();
		MessageResponse m = e.message();
		Set<String> blockedAuthor = messages.viewersWhoCannotSee(m.id(), viewers);
		Set<String> cannotSeeQuote = m.replyTo() == null ? Set.of()
				: messages.viewersWhoCannotSee(m.replyTo().id(), viewers);

		MessageResponse forOthers = new MessageResponse(m.id(), m.content(), m.marker(), false, m.createdAt(),
				m.replyTo());
		MessageResponse withoutQuote = m.replyTo() == null ? forOthers
				: new MessageResponse(m.id(), m.content(), m.marker(), false, m.createdAt(),
						new MessageResponse.QuotedMessage(m.replyTo().id(), null, m.replyTo().createdAt()));

		for (LiveSession s : targets) {
			if (blockedAuthor.contains(s.authorKey())) {
				continue;
			}
			broker.convertAndSendToUser(s.sessionId(), DESTINATION,
					cannotSeeQuote.contains(s.authorKey()) ? withoutQuote : forOthers);
		}
		log.debug("live message {} → {} sessions", m.id(), targets.size());
	}
}
