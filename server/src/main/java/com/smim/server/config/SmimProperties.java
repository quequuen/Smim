package com.smim.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** application.yml 의 smim.* — 값과 근거는 그쪽 주석에 있다 */
@Validated
@ConfigurationProperties("smim")
public record SmimProperties(@NotNull @Valid Experiment experiment, @NotNull @Valid Location location,
		@NotNull @Valid Marker marker, @NotNull @Valid Moderation moderation,
		@NotNull @Valid Admin admin) {

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

	/**
	 * 모더레이션 (#16). 1인 운영이라 수동 검토를 기다릴 수 없다 — 임계치를 넘으면 사람 없이 가려야 한다.
	 *
	 * 신고자 본인에게 숨기는 것은 임계치와 무관하게 신고 즉시 일어난다 (MessageRepository.visibleTo).
	 * 여기 값들은 "모두에게" 가릴 때와 구역을 막을 때만 본다.
	 */
	public record Moderation(@Positive int blindThreshold, @NotNull Duration rateWindow, @Positive int rateMax,
			@Positive int areaThreshold, @Positive int areaRadiusM, @NotNull Duration areaWindow) {
	}

	/**
	 * 관리자 API 토큰. 환경변수로만 준다 — 저장소에 기본값을 두지 않는다.
	 *
	 * 비어 있으면 /admin/** 이 전부 401 이다. 설정을 빠뜨렸을 때 열리는 것이 아니라 닫히게 한다.
	 */
	public record Admin(String token) {

		public boolean configured() {
			return token != null && !token.isBlank();
		}
	}
}
