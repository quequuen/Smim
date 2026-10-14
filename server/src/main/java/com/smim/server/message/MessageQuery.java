package com.smim.server.message;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

/**
 * GET /messages 쿼리 파라미터.
 *
 * @param cursor 이 시각보다 이전 것을 가져온다 (위로 스크롤). 직전 응답의 nextCursor 를 그대로 넣는다.
 *               {@code @DateTimeFormat(iso = DATE_TIME)} 은 소수점 3자리만 받아 마이크로초 커서가 400 이 된다.
 *               Instant 는 Instant.parse 로 자릿수 · 오프셋(+09:00) 과 무관하게 받는다
 */
public record MessageQuery(@NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
		@NotNull @DecimalMin("-180") @DecimalMax("180") Double lon, @NotNull @Positive Integer radius,
		Instant cursor,
		@Min(1) @Max(100) Integer limit) {

	static final int DEFAULT_LIMIT = 50;

	int limitOrDefault() {
		return limit != null ? limit : DEFAULT_LIMIT;
	}
}
