package com.smim.server.presence;

import com.smim.server.common.DeviceKeys;
import com.smim.server.common.GeoPoint;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 하트비트 — 실시간 전달 대상에 포함되기 위한 위치 등록. 근거: docs/api.md 3장
 *
 * PresenceStore 는 트랜잭션 밖에서 부른다 (docs/tracks.md 1장). 여기에는 트랜잭션이 없다.
 */
@RestController
public class PresenceController {

	private static final Logger log = LoggerFactory.getLogger(PresenceController.class);

	private final PresenceStore store;

	public PresenceController(PresenceStore store) {
		this.store = store;
	}

	@PostMapping("/presence")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void heartbeat(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey,
			@Valid @RequestBody PresenceRequest req) {
		String authorKey = DeviceKeys.toAuthorKey(deviceKey);

		// 좌표는 민감 정보라 DEBUG 로만 남긴다
		log.debug("presence session={} author={}… lat={} lon={}", req.sessionId(), authorKey.substring(0, 8),
				req.lat(), req.lon());

		store.upsert(req.sessionId(), authorKey, new GeoPoint(req.lat(), req.lon()));
	}
}
