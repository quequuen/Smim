package com.smim.server.presence;

import com.smim.server.common.DeviceKeys;
import com.smim.server.common.GeoPoint;
import com.smim.server.config.SmimProperties;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 하트비트 — 실시간 전달 대상에 포함되기 위한 위치 등록. 근거: docs/api.md 3장
 *
 * PresenceStore 는 트랜잭션 밖에서 부른다 (docs/tracks.md 1장). 여기에는 트랜잭션이 없다.
 *
 * 응답으로 "이 근처 몇 명" 을 돌려준다. 전용 경로를 만들지 않은 이유는 이미 heartbeatSec 주기로
 * 오는 요청이고, 그 주기가 곧 "지금 있다" 의 정의이기 때문이다 — 같은 데이터를 같은 호출에서 준다.
 */
@RestController
public class PresenceController {

	private static final Logger log = LoggerFactory.getLogger(PresenceController.class);

	private final PresenceStore store;

	private final PresenceProperties presenceProps;

	private final SmimProperties props;

	public PresenceController(PresenceStore store, PresenceProperties presenceProps, SmimProperties props) {
		this.store = store;
		this.presenceProps = presenceProps;
		this.props = props;
	}

	@PostMapping("/presence")
	public PresenceResponse heartbeat(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey,
			@Valid @RequestBody PresenceRequest req) {
		String authorKey = DeviceKeys.toAuthorKey(deviceKey);

		// 좌표는 민감 정보라 DEBUG 로만 남긴다
		log.debug("presence session={} author={}… lat={} lon={}", req.sessionId(), authorKey.substring(0, 8),
				req.lat(), req.lon());

		GeoPoint at = new GeoPoint(req.lat(), req.lon());
		store.upsert(req.sessionId(), authorKey, at);
		return nearby(authorKey, at);
	}

	/**
	 * 화면에 보이는 반경과 같은 반경으로 센다 — 헤더가 "이 근처" 라고 말하고 있으므로 목록과 범위가 달라선 안 된다.
	 *
	 * 하한(nearbyMin)에 못 미치면 숫자를 지운다. 혼자 있는데 "2명" 이 뜨면 방금 들어온
	 * <b>특정인의 존재</b>를 알려주는 셈이고, 300m 안이면 그게 누군지 짐작이 간다.
	 */
	private PresenceResponse nearby(String authorKey, GeoPoint at) {
		int radiusM = props.experiment().groupOf(authorKey).radiusM();
		int count = store.countAuthorsWithin(at, radiusM);
		return count < presenceProps.nearbyMin() ? PresenceResponse.HIDDEN : new PresenceResponse(count);
	}
}
