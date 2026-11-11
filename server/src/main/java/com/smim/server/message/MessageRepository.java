package com.smim.server.message;

import com.smim.server.common.GeoPoint;
import com.smim.server.message.MessageResponse.QuotedMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * message · report 테이블 (V1). PresenceStore 와 같이 JdbcTemplate 으로 쓴다 — 공간 질의가 대부분이라
 * JPA 엔티티로 얻는 것이 없다.
 *
 * :me 는 요청한 사람의 author_key 다. 결과에 author_key 를 담아 나가는 메서드는 없다.
 */
@Repository
public class MessageRepository {

	private static final String POINT = "ST_MakePoint(:lon, :lat)::geography";

	/**
	 * 이 사용자에게 보이는가 — 지운·숨긴 글, 내가 신고한 글, 내가 차단한 작성자의 글은 빠진다 (api.md GET /messages)
	 * 목록 · 인용 · 답글이 모두 이 조건 하나를 쓴다. 따로 쓰면 어긋난다.
	 */
	static String visibleTo(String m) {
		return """
				(%1$s.status = 'visible'
				 AND NOT EXISTS (SELECT 1 FROM report rp WHERE rp.message_id = %1$s.id AND rp.reporter_key = :me)
				 AND NOT EXISTS (SELECT 1 FROM user_block ub
				                 WHERE ub.blocker_key = :me AND ub.blocked_author_key = %1$s.author_key))
				""".formatted(m);
	}

	private final NamedParameterJdbcTemplate jdbc;

	public MessageRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	private static MapSqlParameterSource params(String me, GeoPoint at) {
		return new MapSqlParameterSource().addValue("me", me).addValue("lat", at.lat()).addValue("lon", at.lon());
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime t = rs.getObject(column, OffsetDateTime.class);
		return t != null ? t.toInstant() : null;
	}

	/** 반경 안, 최신순. 인용한 원글이 보이지 않으면 replyTo.content 는 null */
	public List<MessageResponse> findNear(String me, GeoPoint at, int radiusM, Instant before, int limit) {
		MapSqlParameterSource p = params(me, at).addValue("radius", radiusM).addValue("limit", limit);
		String cursor = "";
		if (before != null) {
			cursor = "AND m.created_at < :before";
			p.addValue("before", before.atOffset(ZoneOffset.UTC));
		}
		return jdbc.query("""
				SELECT m.id, m.content, m.marker, m.author_key = :me AS is_mine, m.created_at,
				       q.id AS q_id, CASE WHEN %s THEN q.content END AS q_content, q.created_at AS q_created_at
				FROM message m
				LEFT JOIN message q ON q.id = m.reply_to_id
				WHERE ST_DWithin(m.location, %s, :radius)
				  AND %s
				  %s
				ORDER BY m.created_at DESC
				LIMIT :limit
				""".formatted(visibleTo("q"), POINT, visibleTo("m"), cursor), p, (rs, i) -> {
			long quoteId = rs.getLong("q_id");
			QuotedMessage quote = rs.wasNull() ? null
					: new QuotedMessage(quoteId, rs.getString("q_content"), instant(rs, "q_created_at"));
			return new MessageResponse(rs.getLong("id"), rs.getString("content"), rs.getInt("marker"),
					rs.getBoolean("is_mine"), instant(rs, "created_at"), quote);
		});
	}

	/**
	 * viewers 중 이 글을 볼 수 없는 사람 — 신고했거나 작성자를 차단했다. 실시간 전달에서 visibleTo() 를 대신한다.
	 * 지운 · 숨긴 글은 여기서 보지 않는다 (방금 쓴 글이거나, 쓴 사람이 방금 인용한 글이다).
	 */
	public Set<String> viewersWhoCannotSee(long messageId, Collection<String> viewers) {
		if (viewers.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(jdbc.queryForList("""
				SELECT rp.reporter_key FROM report rp
				WHERE rp.message_id = :id AND rp.reporter_key IN (:viewers)
				UNION
				SELECT ub.blocker_key FROM user_block ub
				JOIN message m ON m.id = :id AND ub.blocked_author_key = m.author_key
				WHERE ub.blocker_key IN (:viewers)
				""", new MapSqlParameterSource().addValue("id", messageId).addValue("viewers", viewers), String.class));
	}

	/** 답글을 달 수 있는 글 — 이 사용자에게 보이는 글만 */
	public Optional<QuotedMessage> findQuotable(String me, long id) {
		return jdbc.query("SELECT m.id, m.content, m.created_at FROM message m WHERE m.id = :id AND "
				+ visibleTo("m"), new MapSqlParameterSource().addValue("me", me).addValue("id", id),
				(rs, i) -> new QuotedMessage(rs.getLong("id"), rs.getString("content"), instant(rs, "created_at")))
			.stream()
			.findFirst();
	}

	public boolean inBannedArea(GeoPoint at) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM banned_area WHERE ST_DWithin(center, %s, radius_m))".formatted(POINT),
				params(null, at), Boolean.class));
	}

	/** 표식 배정 재료. 최신순이라 같은 작성자의 가장 최근 표식이 먼저 나온다 */
	public List<MarkerUse> recentMarkers(GeoPoint at, int areaM, int windowHour) {
		return jdbc.query("""
				SELECT marker, author_key FROM message
				WHERE ST_DWithin(location, %s, :area)
				  AND created_at > now() - make_interval(hours => :hours)
				  AND status = 'visible'
				ORDER BY created_at DESC
				""".formatted(POINT), params(null, at).addValue("area", areaM).addValue("hours", windowHour),
				(rs, i) -> new MarkerUse(rs.getInt("marker"), rs.getString("author_key")));
	}

	public record MarkerUse(int marker, String authorKey) {
	}

	public Created insert(String authorKey, String content, GeoPoint at, int marker, Long replyToId) {
		return jdbc.queryForObject("""
				INSERT INTO message (content, location, author_key, marker, reply_to_id)
				VALUES (:content, %s, :me, :marker, :replyTo)
				RETURNING id, created_at
				""".formatted(POINT),
				params(authorKey, at).addValue("content", content)
					.addValue("marker", marker)
					.addValue("replyTo", replyToId),
				(rs, i) -> new Created(rs.getLong("id"), instant(rs, "created_at")));
	}

	public record Created(long id, Instant createdAt) {
	}

	/** 지우기 전 확인. 동시에 두 번 지우는 요청이 겹치지 않도록 행을 잠근다 */
	public Optional<Ownership> lockOwnership(long id) {
		return jdbc.query("SELECT author_key, status FROM message WHERE id = :id FOR UPDATE",
				new MapSqlParameterSource("id", id),
				(rs, i) -> new Ownership(rs.getString("author_key"), rs.getString("status")))
			.stream()
			.findFirst();
	}

	public record Ownership(String authorKey, String status) {
	}

	/** 행은 남긴다 — 답글의 reply_to_id · 신고 · 차단이 이 글을 가리키고 있다 */
	public void markDeleted(long id) {
		jdbc.update("UPDATE message SET status = 'deleted' WHERE id = :id", new MapSqlParameterSource("id", id));
	}

	/** 신고 · 차단 대상. 지워지지 않고 보이는 글만 */
	public Optional<String> findVisibleAuthor(long id) {
		return jdbc
			.queryForList("SELECT author_key FROM message WHERE id = :id AND status = 'visible'",
					new MapSqlParameterSource("id", id), String.class)
			.stream()
			.findFirst();
	}

	/** @return false 면 이미 신고했다 — UNIQUE (message_id, reporter_key) */
	public boolean insertReport(long messageId, String reporterKey, String reason) {
		return jdbc.update("""
				INSERT INTO report (message_id, reporter_key, reason) VALUES (:id, :me, :reason)
				ON CONFLICT (message_id, reporter_key) DO NOTHING
				""", new MapSqlParameterSource().addValue("id", messageId)
			.addValue("me", reporterKey)
			.addValue("reason", reason)) > 0;
	}

	/** 내 글에 달린 남의 답글. 목록과 같은 필터를 거친다 */
	public List<ReplyResponse> findRepliesTo(String me, int limit) {
		return jdbc.query("""
				SELECT r.id, r.content, r.marker, r.created_at, mine.id AS my_id, mine.content AS my_content
				FROM message mine
				JOIN message r ON r.reply_to_id = mine.id
				WHERE mine.author_key = :me AND mine.status = 'visible'
				  AND r.author_key <> :me
				  AND %s
				ORDER BY r.created_at DESC
				LIMIT :limit
				""".formatted(visibleTo("r")), new MapSqlParameterSource().addValue("me", me).addValue("limit", limit),
				(rs, i) -> new ReplyResponse(rs.getLong("id"), rs.getString("content"), rs.getInt("marker"),
						instant(rs, "created_at"),
						new ReplyResponse.MyMessage(rs.getLong("my_id"), rs.getString("my_content"))));
	}

	// ── 모더레이션 (#16) ──────────────────────────────────────────

	/** 도배 제한 — 이 기기가 최근 window 안에 쓴 글 수. 지운 글도 센다 (쓰고 지우기를 반복할 수 있다) */
	public int countRecentBy(String authorKey, Duration window) {
		Integer n = jdbc.queryForObject("""
				SELECT count(*) FROM message
				WHERE author_key = :me AND created_at > now() - make_interval(secs => :secs)
				""", new MapSqlParameterSource().addValue("me", authorKey).addValue("secs", window.toSeconds()),
				Integer.class);
		return n == null ? 0 : n;
	}

	/** 서로 다른 기기 몇 대가 이 글을 신고했나. UNIQUE (message_id, reporter_key) 라 행 수가 곧 기기 수다 */
	public int countReports(long messageId) {
		Integer n = jdbc.queryForObject("SELECT count(*) FROM report WHERE message_id = :id",
				new MapSqlParameterSource("id", messageId), Integer.class);
		return n == null ? 0 : n;
	}

	/**
	 * 모두에게 가린다. 보이는 글만 바꾸므로 이미 가려졌거나 지워진 글에는 아무 일도 없다.
	 *
	 * @return 이번 호출이 실제로 가렸으면 true — 자동 구역 차단을 한 번만 검사하기 위해 필요하다
	 */
	public boolean hide(long id) {
		return jdbc.update("UPDATE message SET status = 'hidden' WHERE id = :id AND status = 'visible'",
				new MapSqlParameterSource("id", id)) > 0;
	}

	/** 관리자 해제. 오탐으로 가려진 글을 되돌린다. 지운 글은 되살리지 않는다 */
	public boolean unhide(long id) {
		return jdbc.update("UPDATE message SET status = 'visible' WHERE id = :id AND status = 'hidden'",
				new MapSqlParameterSource("id", id)) > 0;
	}

	/** 자동 구역 차단 판단에 쓸 좌표. 지운 글은 세지 않으므로 여기서도 뺀다 */
	public Optional<GeoPoint> locationOf(long id) {
		return jdbc
			.query("""
					SELECT ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lon
					FROM message WHERE id = :id AND status <> 'deleted'
					""", new MapSqlParameterSource("id", id),
					(rs, i) -> new GeoPoint(rs.getDouble("lat"), rs.getDouble("lon")))
			.stream()
			.findFirst();
	}

	/** 이 반경 안에서 최근 window 동안 가려진 글 수 (idx_message_hidden) */
	public int countHiddenNear(GeoPoint at, int radiusM, Duration window) {
		Integer n = jdbc.queryForObject("""
				SELECT count(*) FROM message
				WHERE status = 'hidden'
				  AND ST_DWithin(location, %s, :radius)
				  AND created_at > now() - make_interval(secs => :secs)
				""".formatted(POINT),
				params(null, at).addValue("radius", radiusM).addValue("secs", window.toSeconds()), Integer.class);
		return n == null ? 0 : n;
	}

	public long insertBannedArea(GeoPoint center, int radiusM, String reason, String source) {
		Long id = jdbc.queryForObject("""
				INSERT INTO banned_area (center, radius_m, reason, source)
				VALUES (%s, :radius, :reason, :source)
				RETURNING id
				""".formatted(POINT),
				params(null, center).addValue("radius", radiusM).addValue("reason", reason).addValue("source", source),
				Long.class);
		return id == null ? 0 : id;
	}

	public List<BannedArea> listBannedAreas() {
		return jdbc.query("""
				SELECT id, ST_Y(center::geometry) AS lat, ST_X(center::geometry) AS lon,
				       radius_m, reason, source, created_at
				FROM banned_area ORDER BY created_at DESC
				""", (rs, i) -> new BannedArea(rs.getLong("id"), rs.getDouble("lat"), rs.getDouble("lon"),
						rs.getInt("radius_m"), rs.getString("reason"), rs.getString("source"),
						instant(rs, "created_at")));
	}

	/** 좌표를 담고 나간다 — 관리자 전용이다. 일반 응답에는 절대 넣지 않는다 (D4) */
	public record BannedArea(long id, double lat, double lon, int radiusM, String reason, String source,
			Instant createdAt) {
	}

	public boolean deleteBannedArea(long id) {
		return jdbc.update("DELETE FROM banned_area WHERE id = :id", new MapSqlParameterSource("id", id)) > 0;
	}

	/** 관리자 검토 대기열 — 신고가 붙은 글을 많이 신고된 순으로 */
	public List<ReportedMessage> findReported(int limit) {
		return jdbc.query("""
				SELECT m.id, m.content, m.status, m.created_at,
				       count(r.id) AS reports, min(r.created_at) AS first_reported,
				       string_agg(DISTINCT r.reason, ',' ORDER BY r.reason) AS reasons
				FROM message m JOIN report r ON r.message_id = m.id
				WHERE m.status <> 'deleted'
				GROUP BY m.id, m.content, m.status, m.created_at
				ORDER BY count(r.id) DESC, min(r.created_at)
				LIMIT :limit
				""", new MapSqlParameterSource("limit", limit),
				(rs, i) -> new ReportedMessage(rs.getLong("id"), rs.getString("content"), rs.getString("status"),
						rs.getInt("reports"), rs.getString("reasons"), instant(rs, "first_reported"),
						instant(rs, "created_at")));
	}

	public record ReportedMessage(long id, String content, String status, int reports, String reasons,
			Instant firstReportedAt, Instant createdAt) {
	}
}
