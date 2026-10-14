package com.smim.server.presence;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료 처리 ② — Redis TTL 을 대신한다. 근거: docs/tracks.md 2장
 *
 * Redis 구현은 TTL 이 있으므로 purgeStale 을 no-op 으로 두면 된다. 이 클래스는 그대로 둔다.
 */
@Component
public class PresenceJanitor {

	private static final Logger log = LoggerFactory.getLogger(PresenceJanitor.class);

	private final PresenceStore store;

	private final PresenceProperties props;

	public PresenceJanitor(PresenceStore store, PresenceProperties props) {
		this.store = store;
		this.props = props;
	}

	@Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
	void purge() {
		int n = store.purgeStale(props.purgeAfter());
		if (n > 0) {
			log.info("presence purged {} stale sessions", n);
		}
	}
}
