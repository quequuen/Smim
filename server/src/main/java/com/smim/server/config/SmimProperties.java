package com.smim.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** application.yml 의 smim.* — 값과 근거는 그쪽 주석에 있다 */
@Validated
@ConfigurationProperties("smim")
public record SmimProperties(@NotNull @Valid Experiment experiment, @NotNull @Valid Location location,
		@NotNull @Valid Marker marker) {

	public record Experiment(@NotEmpty List<@Valid Group> groups) {

		/** GET /messages 가 받아들이는 반경 상한. 이보다 넓게 긁어 가지 못하게 한다 */
		public int maxRadiusM() {
			return groups.stream().mapToInt(Group::radiusM).max().orElseThrow();
		}

		/**
		 * hash(author_key) % 그룹 수 (location-policy.md 4장). author_key 가 이미 SHA-256 이므로 다시 해시하지 않고
		 * 앞 32비트를 쓴다. 언어 · JVM 에 무관하게 같은 값이 나와야 로그 분석 쪽에서도 그룹을 재현할 수 있다.
		 */
		public Group groupOf(String authorKey) {
			long h = Long.parseLong(authorKey.substring(0, 8), 16);
			return groups.get((int) (h % groups.size()));
		}
	}

	public record Group(@NotBlank String name, @Positive int radiusM, @Positive int keepRadiusM,
			@Positive int moveThresholdM) {
	}

	public record Location(@Positive int heartbeatSec) {
	}

	public record Marker(@Positive int windowHour, @Positive int areaM) {
	}
}
