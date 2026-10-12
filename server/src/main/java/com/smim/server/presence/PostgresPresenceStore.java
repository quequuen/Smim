package com.smim.server.presence;

import com.smim.server.common.GeoPoint;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 트랙 B — active_session 테이블 (V2). 근거: docs/tracks.md 2장
 *
 * JPA 를 쓰지 않는다. 엔티티가 생기면 인터페이스 밖으로 새어 나가기 쉽고, 쿼리가 몇 개 안 된다.
 */
@Component
@ConditionalOnProperty(name = "smim.presence.store", havingValue = "postgres", matchIfMissing = true)
public class PostgresPresenceStore implements PresenceStore {

	private static final String POINT = "ST_MakePoint(:lon, :lat)::geography";

	private final NamedParameterJdbcTemplate jdbc;

	private final PresenceProperties props;

	public PostgresPresenceStore(NamedParameterJdbcTemplate jdbc, PresenceProperties props) {
		this.jdbc = jdbc;
		this.props = props;
	}

	/**
	 * 하트비트 대부분은 제자리다. 그때는 updated_at 만 갱신해 location(GiST 인덱스)을 건드리지 않는다 — HOT update.
	 * 저장된 위치에서 moveToleranceM 이상 벗어났거나 처음 보는 세션일 때만 위치를 쓴다.
	 */
	@Override
	public void upsert(String sessionId, String authorKey, GeoPoint location) {
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", sessionId)
			.addValue("author", authorKey)
			.addValue("lat", location.lat())
			.addValue("lon", location.lon())
			.addValue("tolerance", props.moveToleranceM());

		int touched = jdbc.update("""
				UPDATE active_session SET updated_at = now()
				WHERE session_id = :id AND author_key = :author
				  AND ST_DWithin(location, %s, :tolerance)
				""".formatted(POINT), p);
		if (touched > 0) {
			return;
		}

		// 남의 세션이면 WHERE 에 걸려 아무것도 바뀌지 않는다
		jdbc.update("""
				INSERT INTO active_session (session_id, author_key, location)
				VALUES (:id, :author, %s)
				ON CONFLICT (session_id) DO UPDATE
				  SET location = EXCLUDED.location, updated_at = now()
				  WHERE active_session.author_key = EXCLUDED.author_key
				""".formatted(POINT), p);
	}

	@Override
	public void touch(String sessionId) {
		jdbc.update("UPDATE active_session SET updated_at = now() WHERE session_id = :id",
				new MapSqlParameterSource("id", sessionId));
	}

	/** 만료 처리 ③ — 정리(purge) 전이라도 조회에서 유령 세션을 거른다 */
	@Override
	public List<String> findSessionsWithin(GeoPoint center, int radiusM) {
		return jdbc.queryForList("""
				SELECT session_id FROM active_session
				WHERE ST_DWithin(location, %s, :radius)
				  AND updated_at > now() - make_interval(secs => :staleSec)
				""".formatted(POINT),
				new MapSqlParameterSource().addValue("lat", center.lat())
					.addValue("lon", center.lon())
					.addValue("radius", radiusM)
					.addValue("staleSec", props.staleAfter().toSeconds()),
				String.class);
	}

	/** 만료 처리 ① — WebSocket 연결 종료 시 (#12) */
	@Override
	public void remove(String sessionId) {
		jdbc.update("DELETE FROM active_session WHERE session_id = :id", new MapSqlParameterSource("id", sessionId));
	}

	/** 만료 처리 ② — PresenceJanitor 가 주기적으로 부른다 */
	@Override
	public int purgeStale(Duration olderThan) {
		return jdbc.update("DELETE FROM active_session WHERE updated_at < now() - make_interval(secs => :sec)",
				new MapSqlParameterSource("sec", olderThan.toSeconds()));
	}
}
