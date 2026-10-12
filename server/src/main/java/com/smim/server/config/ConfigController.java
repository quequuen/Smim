package com.smim.server.config;

import com.smim.server.common.DeviceKeys;
import java.util.List;
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
		SmimProperties.Group g = groupOf(DeviceKeys.toAuthorKey(deviceKey));
		return new RuntimeConfig(g.name(), g.radiusM(), g.keepRadiusM(), g.moveThresholdM(),
				props.location().heartbeatSec(), props.marker().windowHour());
	}

	/**
	 * hash(author_key) % 그룹 수. author_key 가 이미 SHA-256 이므로 다시 해시하지 않고 앞 32비트를 쓴다.
	 * 언어·JVM 에 무관하게 같은 값이 나와야 로그 분석 쪽에서도 그룹을 재현할 수 있다.
	 */
	private SmimProperties.Group groupOf(String authorKey) {
		List<SmimProperties.Group> groups = props.experiment().groups();
		long h = Long.parseLong(authorKey.substring(0, 8), 16);
		return groups.get((int) (h % groups.size()));
	}
}
