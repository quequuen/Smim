package com.smim.server.config;

import com.smim.server.presence.PresenceProperties;
import java.time.Duration;
import java.util.List;

/**
 * MockMvc standaloneSetup 으로 컨트롤러를 직접 만드는 테스트가 쓰는 설정 뭉치.
 *
 * 설정 레코드에 항목이 늘 때마다 테스트가 줄줄이 깨지는 것을 막기 위해 한곳에 모았다.
 * 각 테스트는 자기가 보는 부분만 인자로 바꾼다.
 */
public final class TestProps {

	public static final List<SmimProperties.Group> GROUPS = List.of(new SmimProperties.Group("A", 100, 150, 15),
			new SmimProperties.Group("B", 150, 225, 20), new SmimProperties.Group("C", 300, 450, 45),
			new SmimProperties.Group("D", 500, 750, 70));

	private TestProps() {
	}

	public static SmimProperties smim() {
		return smim(new SmimProperties.Experiment(GROUPS));
	}

	public static SmimProperties smim(SmimProperties.Experiment experiment) {
		return new SmimProperties(experiment, new SmimProperties.Location(45), new SmimProperties.Marker(6, 300),
				new SmimProperties.Moderation(5, Duration.ofMinutes(1), 5, 3, 100, Duration.ofHours(24)),
				new SmimProperties.Admin(null));
	}

	/** @param nearbyMin "이 근처 N명" 하한 — 이보다 적으면 숫자를 지워 보낸다 */
	public static PresenceProperties presence(int nearbyMin) {
		return new PresenceProperties("postgres", Duration.ofMinutes(2), Duration.ofMinutes(10), 10, nearbyMin);
	}
}
