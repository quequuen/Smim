package com.smim.server.presence;

import static org.assertj.core.api.Assertions.assertThat;

import com.smim.server.common.GeoPoint;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 PostGIS 에 붙는다 (docker compose up -d postgres).
 * 개발 DB 를 같이 쓰므로 test- 로 시작하는 세션만 만들고 지운다.
 */
@SpringBootTest
class PostgresPresenceStoreTest {

	private static final String ME = "a".repeat(64);

	private static final String OTHER = "b".repeat(64);

	// 홍대입구역 근처. 위도 0.001° ≈ 111m
	private static final GeoPoint HERE = new GeoPoint(37.5563, 126.9238);

	@Autowired
	PresenceStore store;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM active_session WHERE session_id LIKE 'test-%'");
	}

	private static GeoPoint north(double meters) {
		return new GeoPoint(HERE.lat() + meters / 111_000, HERE.lon());
	}

	private Map<String, Object> row(String sessionId) {
		return jdbc.queryForMap("""
				SELECT ST_Y(location::geometry) AS lat, author_key, updated_at
				FROM active_session WHERE session_id = ?
				""", sessionId);
	}

	private void age(String sessionId, Duration ago) {
		jdbc.update("UPDATE active_session SET updated_at = now() - make_interval(secs => ?) WHERE session_id = ?",
				ago.toSeconds(), sessionId);
	}

	@Test
	void 반경_안의_세션만_찾는다() {
		store.upsert("test-near", ME, north(100));
		store.upsert("test-far", ME, north(400));

		assertThat(store.findSessionsWithin(HERE, 300)).contains("test-near").doesNotContain("test-far");
	}

	@Test
	void 제자리_하트비트는_위치를_다시_쓰지_않는다() {
		store.upsert("test-s", ME, HERE);
		age("test-s", Duration.ofMinutes(1));

		store.upsert("test-s", ME, north(5)); // 허용 오차(10m) 안

		Map<String, Object> r = row("test-s");
		assertThat((double) r.get("lat")).isEqualTo(HERE.lat());
		assertThat(store.findSessionsWithin(HERE, 100)).contains("test-s"); // updated_at 은 갱신됨
	}

	@Test
	void 움직였으면_위치를_갱신한다() {
		store.upsert("test-s", ME, HERE);
		store.upsert("test-s", ME, north(500));

		assertThat(store.findSessionsWithin(HERE, 300)).doesNotContain("test-s");
		assertThat(store.findSessionsWithin(north(500), 50)).contains("test-s");
	}

	@Test
	void 남의_세션은_덮어쓸_수_없다() {
		store.upsert("test-s", ME, HERE);
		store.upsert("test-s", OTHER, north(500));

		Map<String, Object> r = row("test-s");
		assertThat(r.get("author_key")).isEqualTo(ME);
		assertThat((double) r.get("lat")).isEqualTo(HERE.lat());
	}

	@Test
	void 오래_갱신되지_않은_세션은_찾지_않고_touch_하면_돌아온다() {
		store.upsert("test-s", ME, HERE);
		age("test-s", Duration.ofMinutes(3)); // stale-after 2m 초과

		assertThat(store.findSessionsWithin(HERE, 100)).doesNotContain("test-s");

		store.touch("test-s");
		assertThat(store.findSessionsWithin(HERE, 100)).contains("test-s");
	}

	@Test
	void purgeStale_은_오래된_세션만_지운다() {
		store.upsert("test-old", ME, HERE);
		store.upsert("test-new", ME, HERE);
		age("test-old", Duration.ofMinutes(11));

		assertThat(store.purgeStale(Duration.ofMinutes(10))).isGreaterThanOrEqualTo(1);

		assertThat(jdbc.queryForList("SELECT session_id FROM active_session WHERE session_id LIKE 'test-%'",
				String.class)).containsExactly("test-new");
	}

	@Test
	void remove_하면_사라진다() {
		store.upsert("test-s", ME, HERE);
		store.remove("test-s");

		assertThat(store.findSessionsWithin(HERE, 100)).doesNotContain("test-s");
	}
}
