package com.smim.server.config;

import com.smim.server.common.DeviceKeys;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 런타임 설정과 A/B 그룹 배정. 근거: docs/location-policy.md 3~4장
 *
 * 앱은 실패하면 내장 기본값으로 폴백하므로, 여기서 오류가 나도 앱이 멈추지는 않는다.
 */
@RestController
public class ConfigController {

	private final SmimProperties props;

	public ConfigController(SmimProperties props) {
		this.props = props;
	}

	@GetMapping("/config")
	public RuntimeConfig config(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey) {
		SmimProperties.Group g = props.experiment().groupOf(DeviceKeys.toAuthorKey(deviceKey));
		return new RuntimeConfig(g.name(), g.radiusM(), g.keepRadiusM(), g.moveThresholdM(),
				props.location().heartbeatSec(), props.marker().windowHour());
	}
}
