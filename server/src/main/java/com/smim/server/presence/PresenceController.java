package com.smim.server.presence;

import com.smim.server.common.DeviceKeys;
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
 * 지금은 받은 좌표를 확인만 한다 (#7). 저장은 PresenceStore(#8)가 붙으면서 시작한다.
 */
@RestController
public class PresenceController {

	private static final Logger log = LoggerFactory.getLogger(PresenceController.class);

	@PostMapping("/presence")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void heartbeat(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey,
			@Valid @RequestBody PresenceRequest req) {
		String authorKey = DeviceKeys.toAuthorKey(deviceKey);

		// 좌표는 민감 정보라 DEBUG 로만 남긴다
		log.debug("presence session={} author={}… lat={} lon={}", req.sessionId(), authorKey.substring(0, 8),
				req.lat(), req.lon());

		// TODO(#8): presenceStore.upsert(req.sessionId(), authorKey, point(req.lon(), req.lat()))
	}
}
