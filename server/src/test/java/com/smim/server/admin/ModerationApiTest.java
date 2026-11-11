package com.smim.server.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smim.server.common.DeviceKeys;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 모더레이션 (#16) — 도배 제한 · 자동 블라인드 · 자동 구역 차단 · 관리자 API.
 *
 * 임계치를 운영값(5건)보다 낮춰 돌린다. 검증 대상은 '5' 라는 숫자가 아니라 **임계치를 넘으면
 * 사람 없이 가려진다는 동작**이고, 운영값은 application.yml 한 곳에만 둔다.
 *
 * MessageApiTest 와 같은 개발 DB 를 쓰므로 좌표와 기기 키를 겹치지 않게 잡는다.
 */
@SpringBootTest(properties = { "smim.moderation.blind-threshold=3", "smim.moderation.rate-max=3",
		"smim.moderation.rate-window=1m", "smim.moderation.area-threshold=2", "smim.moderation.area-radius-m=100",
		"smim.moderation.area-window=24h", "smim.admin.token=test-admin-token" })
class ModerationApiTest {

	/** 남태평양. MessageApiTest(-48.8, -123.4) 의 정리 반경 50km 밖이다 */
	private static final double LAT = -46.5;

	private static final double LON = -119.0;

	private static final String TOKEN = "test-admin-token";

	/** 이 테스트가 만든 금지 구역만 지우기 위한 표식 */
	private static final String REASON = "moderation-test";

	private static final String A = key(1);

	private static final String B = key(2);

	private static final String C = key(3);

	private static final String D = key(4);

	private static final String E = key(5);

	private static final String F = key(6);

	@Autowired
	WebApplicationContext context;

	@Autowired
	JdbcTemplate jdbc;

	MockMvc mvc;

	private static String key(int seed) {
		byte[] bytes = new byte[32];
		bytes[31] = (byte) seed;
		bytes[0] = 0x6a; // MessageApiTest 는 0x7e 를 쓴다
		return Base64.getEncoder().encodeToString(bytes);
	}

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.webAppContextSetup(context).build();
		cleanUp();
	}

	@AfterEach
	void cleanUp() {
		String area = "ST_DWithin(location, ST_MakePoint(%s, %s)::geography, 50000)".formatted(LON, LAT);
		String ids = "SELECT id FROM message WHERE " + area;
		jdbc.update("DELETE FROM report WHERE message_id IN (" + ids + ")");
		jdbc.update("DELETE FROM user_block WHERE message_id IN (" + ids + ")");
		jdbc.update("UPDATE message SET reply_to_id = NULL WHERE " + area);
		jdbc.update("DELETE FROM message WHERE " + area);
		// 자동 등록된 것까지 함께 지운다 — 남으면 다음 테스트의 글쓰기가 403 이 된다
		jdbc.update("DELETE FROM banned_area WHERE reason = ? OR (source = 'auto' AND "
				+ "ST_DWithin(center, ST_MakePoint(%s, %s)::geography, 50000))".formatted(LON, LAT), REASON);
	}

	// ── 도우미 ─────────────────────────────────────────────

	private ResultActions write(String who, String content) throws Exception {
		return write(who, content, 0);
	}

	private ResultActions write(String who, String content, double northM) throws Exception {
		String body = """
				{ "content": "%s", "lat": %s, "lon": %s }
				""".formatted(content, LAT + northM / 111_000, LON);
		return mvc.perform(post("/messages").header(DeviceKeys.HEADER, who)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private long writeId(String who, String content) throws Exception {
		String res = write(who, content).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(res, "$.id")).longValue();
	}

	private ResultActions report(String who, long id) throws Exception {
		return mvc.perform(post("/messages/{id}/report", id).header(DeviceKeys.HEADER, who)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"abuse\"}"));
	}

	private List<String> contents(String who) throws Exception {
		String res = mvc.perform(get("/messages?lat={lat}&lon={lon}&radius=300", LAT, LON)
			.header(DeviceKeys.HEADER, who)).andReturn().getResponse().getContentAsString();
		return JsonPath.read(res, "$.messages[*].content");
	}

	private String statusOf(long id) {
		return jdbc.queryForObject("SELECT status FROM message WHERE id = ?", String.class, id);
	}

	private int bannedAreaCount() {
		Integer n = jdbc.queryForObject("SELECT count(*) FROM banned_area WHERE ST_DWithin(center, "
				+ "ST_MakePoint(%s, %s)::geography, 50000)".formatted(LON, LAT), Integer.class);
		return n == null ? 0 : n;
	}

	// ── 도배 제한 ──────────────────────────────────────────

	@Test
	void 창_안에서_상한을_넘겨_쓰면_429_다() throws Exception {
		writeId(A, "1");
		writeId(A, "2");
		writeId(A, "3"); // rate-max = 3

		write(A, "4").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void 도배_제한은_기기별로_따로_센다() throws Exception {
		writeId(A, "1");
		writeId(A, "2");
		writeId(A, "3");

		// A 가 막혔다고 B 까지 막히면 안 된다
		write(B, "B 의 첫 글").andExpect(status().isCreated());
	}

	// ── 신고 ──────────────────────────────────────────────

	@Test
	void 신고하면_신고자에게만_먼저_숨는다() throws Exception {
		long id = writeId(A, "문제의 글");

		report(B, id).andExpect(status().isNoContent());

		assertThat(statusOf(id)).isEqualTo("visible"); // 아직 모두에게 보인다
		assertThat(contents(B)).doesNotContain("문제의 글"); // 신고한 사람에게만
		assertThat(contents(C)).contains("문제의 글");
	}

	@Test
	void 같은_기기가_두_번_신고하면_409_다() throws Exception {
		long id = writeId(A, "문제의 글");

		report(B, id).andExpect(status().isNoContent());
		report(B, id).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
	}

	@Test
	void 신고가_임계치를_넘기면_모두에게_가려진다() throws Exception {
		long id = writeId(A, "문제의 글");

		report(B, id).andExpect(status().isNoContent());
		report(C, id).andExpect(status().isNoContent());
		assertThat(statusOf(id)).isEqualTo("visible"); // 2건까지는 그대로

		report(D, id).andExpect(status().isNoContent()); // blind-threshold = 3

		assertThat(statusOf(id)).isEqualTo("hidden");
		assertThat(contents(E)).doesNotContain("문제의 글"); // 신고한 적 없는 사람에게도
	}

	@Test
	void 가려진_글은_더_신고할_수_없다() throws Exception {
		long id = writeId(A, "문제의 글");
		report(B, id);
		report(C, id);
		report(D, id);

		// findVisibleAuthor 가 visible 만 보므로 404 — 이미 처리된 글이다
		report(E, id).andExpect(status().isNotFound());
	}

	// ── 자동 구역 차단 ─────────────────────────────────────

	@Test
	void 가려진_글이_한_자리에_쌓이면_구역이_자동으로_막힌다() throws Exception {
		long first = writeId(A, "첫 번째");
		report(B, first);
		report(C, first);
		report(D, first);
		assertThat(bannedAreaCount()).isZero(); // 1건으로는 막히지 않는다 (area-threshold = 2)

		long second = writeId(E, "두 번째");
		report(B, second);
		report(C, second);
		report(D, second);

		assertThat(bannedAreaCount()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT source FROM banned_area WHERE ST_DWithin(center, "
				+ "ST_MakePoint(%s, %s)::geography, 50000)".formatted(LON, LAT), String.class)).isEqualTo("auto");

		// 막힌 뒤에는 그 자리에 쓸 수 없다
		write(F, "막힌 뒤").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BANNED_AREA"));
	}

	@Test
	void 반경_밖에서_가려진_글은_같이_세지_않는다() throws Exception {
		long here = writeId(A, "여기");
		report(B, here);
		report(C, here);
		report(D, here);

		// area-radius-m = 100. 500m 떨어뜨리면 같은 구역이 아니다
		String far = """
				{ "content": "멀리", "lat": %s, "lon": %s }
				""".formatted(LAT + 500.0 / 111_000, LON);
		String res = mvc
			.perform(post("/messages").header(DeviceKeys.HEADER, E)
				.contentType(MediaType.APPLICATION_JSON)
				.content(far))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long farId = ((Number) JsonPath.read(res, "$.id")).longValue();

		report(B, farId);
		report(C, farId);
		report(D, farId);

		assertThat(statusOf(farId)).isEqualTo("hidden"); // 가려지기는 했다
		assertThat(bannedAreaCount()).isZero(); // 그래도 구역은 안 막힌다
	}

	// ── 관리자 ────────────────────────────────────────────

	@Test
	void 토큰_없이는_관리자_API_에_닿을_수_없다() throws Exception {
		mvc.perform(get("/admin/reports")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("NOT_ADMIN"));
		mvc.perform(get("/admin/reports").header(AdminAuth.HEADER, "틀린토큰"))
			.andExpect(status().isUnauthorized());
		mvc.perform(post("/admin/messages/{id}/hide", 1)).andExpect(status().isUnauthorized());
		mvc.perform(get("/admin/banned-areas")).andExpect(status().isUnauthorized());
	}

	@Test
	void 관리자가_숨기고_되돌릴_수_있다() throws Exception {
		long id = writeId(A, "숨길 글");

		mvc.perform(post("/admin/messages/{id}/hide", id).header(AdminAuth.HEADER, TOKEN))
			.andExpect(status().isNoContent());
		assertThat(statusOf(id)).isEqualTo("hidden");
		assertThat(contents(B)).doesNotContain("숨길 글");

		mvc.perform(post("/admin/messages/{id}/unhide", id).header(AdminAuth.HEADER, TOKEN))
			.andExpect(status().isNoContent());
		assertThat(statusOf(id)).isEqualTo("visible");
		assertThat(contents(B)).contains("숨길 글");
	}

	@Test
	void 자동으로_가려진_글도_관리자가_되돌릴_수_있다() throws Exception {
		long id = writeId(A, "오탐");
		report(B, id);
		report(C, id);
		report(D, id);
		assertThat(statusOf(id)).isEqualTo("hidden");

		mvc.perform(post("/admin/messages/{id}/unhide", id).header(AdminAuth.HEADER, TOKEN))
			.andExpect(status().isNoContent());

		assertThat(contents(E)).contains("오탐");
	}

	@Test
	void 검토_대기열에_신고된_글이_많은_순으로_나온다() throws Exception {
		long one = writeId(A, "한 건");
		long two = writeId(A, "두 건");
		report(B, one);
		report(B, two);
		report(C, two);

		mvc.perform(get("/admin/reports").header(AdminAuth.HEADER, TOKEN))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reports[0].id").value((int) two))
			.andExpect(jsonPath("$.reports[0].reports").value(2))
			.andExpect(jsonPath("$.reports[0].reasons").value("abuse"))
			.andExpect(jsonPath("$.reports[1].id").value((int) one));
	}

	@Test
	void 관리자가_금지_구역을_등록하고_지울_수_있다() throws Exception {
		write(A, "막히기 전").andExpect(status().isCreated());

		String body = """
				{ "lat": %s, "lon": %s, "radiusM": 100, "reason": "%s" }
				""".formatted(LAT, LON, REASON);
		String res = mvc
			.perform(post("/admin/banned-areas").header(AdminAuth.HEADER, TOKEN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		long areaId = ((Number) JsonPath.read(res, "$.id")).longValue();

		write(B, "막힌 뒤").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BANNED_AREA"));

		mvc.perform(get("/admin/banned-areas").header(AdminAuth.HEADER, TOKEN))
			.andExpect(jsonPath("$.areas[?(@.id == %d)].source".formatted(areaId)).value("manual"));

		mvc.perform(delete("/admin/banned-areas/{id}", areaId).header(AdminAuth.HEADER, TOKEN))
			.andExpect(status().isNoContent());

		write(C, "풀린 뒤").andExpect(status().isCreated());
	}

	@Test
	void 없는_금지_구역을_지우면_404_다() throws Exception {
		mvc.perform(delete("/admin/banned-areas/{id}", 999_999_999L).header(AdminAuth.HEADER, TOKEN))
			.andExpect(status().isNotFound());
	}
}
