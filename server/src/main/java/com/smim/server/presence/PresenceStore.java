package com.smim.server.presence;

import com.smim.server.common.GeoPoint;
import java.time.Duration;
import java.util.List;

/**
 * 접속자 현재 위치. 근거: docs/tracks.md 1장
 *
 * 구현은 smim.presence.store 로 고른다 — postgres(트랙 B) | redis(트랙 A, 8주차).
 *
 * 규칙 두 가지 (어기면 8주차에 뒤엎게 된다)
 * 1. 평범한 타입만 주고받는다 — JPA 엔티티 · Pageable · EntityManager 금지
 * 2. 트랜잭션 밖에서 호출한다 — Redis 는 JPA 트랜잭션에 참여하지 않는다
 */
public interface PresenceStore {

	/**
	 * 위치 등록. 하트비트마다 불린다.
	 * 같은 sessionId 라도 authorKey 가 다르면 무시한다 — 남의 세션 위치를 덮어쓸 수 없다.
	 */
	void upsert(String sessionId, String authorKey, GeoPoint location);

	/** 위치는 그대로 두고 살아 있음만 알린다 (WebSocket ping 등) */
	void touch(String sessionId);

	/** 반경 안의 살아 있는 세션. 오래 갱신되지 않은 유령 세션은 빠진다 */
	List<String> findSessionsWithin(GeoPoint center, int radiusM);

	void remove(String sessionId);

	/** @return 지운 세션 수 */
	int purgeStale(Duration olderThan);
}
