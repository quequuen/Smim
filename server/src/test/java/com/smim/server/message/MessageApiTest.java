package com.smim.server.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
 * 메시지 · 신고 · 차단 · 답글을 HTTP 부터 실제 PostGIS 까지 (docker compose up -d postgres).
 *
 * 개발 DB 를 같이 쓰므로 사람이 없는 바다 위 좌표에서만 쓰고, 그 주변만 지운다.
 */
@SpringBootTest
class MessageApiTest {

	// 남태평양. 위도 0.001° ≈ 111m
	private static final double LAT = -48.8;

	private static final double LON = -123.4;

	private static final String A = key(1);

	private static final String B = key(2);

	private static final String C = key(3);

	@Autowired
	WebApplicationContext context;

	@Autowired
	JdbcTemplate jdbc;

	MockMvc mvc;

	private static String key(int seed) {
		byte[] bytes = new byte[32];
		bytes[31] = (byte) seed;
		bytes[0] = 0x7e; // 다른 테스트의 키와 겹치지 않게
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
		jdbc.update("DELETE FROM banned_area WHERE reason = 'test'");
	}

	// ── 도우미 ─────────────────────────────────────────────

	private ResultActions write(String who, String content, double northM, Long replyToId) throws Exception {
		String body = """
				{ "content": "%s", "lat": %s, "lon": %s, "replyToId": %s }
				""".formatted(content, LAT + northM / 111_000, LON, replyToId);
		return mvc.perform(post("/messages").header(DeviceKeys.HEADER, who)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private long writeId(String who, String content) throws Exception {
		return writeId(who, content, null);
	}

	private long writeId(String who, String content, Long replyToId) throws Exception {
		String res = write(who, content, 0, replyToId).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(res, "$.id")).longValue();
	}

	private ResultActions list(String who) throws Exception {
		return list(who, "");
	}

	private ResultActions list(String who, String extra) throws Exception {
		return mvc.perform(get("/messages?lat={lat}&lon={lon}&radius=300" + extra, LAT, LON)
			.header(DeviceKeys.HEADER, who));
	}

	private List<String> contents(String who) throws Exception {
		return JsonPath.read(list(who).andReturn().getResponse().getContentAsString(), "$.messages[*].content");
	}

	private ResultActions json(String who, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
			String body) throws Exception {
		return mvc.perform(req.header(DeviceKeys.HEADER, who).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	// ── 작성 · 조회 ───────────────────────────────────────

	@Test
	void 쓰면_반경_안에서_최신순으로_보이고_isMine_이_구분된다() throws Exception {
		writeId(A, "첫 번째");
		writeId(B, "두 번째");

		list(A).andExpect(status().isOk())
			.andExpect(jsonPath("$.messages[*].content").value(org.hamcrest.Matchers.contains("두 번째", "첫 번째")))
			.andExpect(jsonPath("$.messages[0].isMine").value(false))
			.andExpect(jsonPath("$.messages[1].isMine").value(true))
			.andExpect(jsonPath("$.messages[0].createdAt").isString())
			.andExpect(jsonPath("$.messages[0].replyTo").value(nullValue()))
			.andExpect(jsonPath("$.nextCursor").value(nullValue()));
	}

	@Test
	void 응답에_author_key_와_좌표가_없다() throws Exception {
		writeId(A, "흔적");
		String body = list(B).andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(DeviceKeys.toAuthorKey(A), "author", "lat", "lon", "location");
	}

	@Test
	void 반경_밖의_글은_보이지_않는다() throws Exception {
		write(A, "가까움", 100, null).andExpect(status().isCreated());
		write(A, "멀리", 1000, null).andExpect(status().isCreated());

		assertThat(contents(B)).containsExactly("가까움");
	}

	@Test
	void 커서로_이전_글을_이어서_가져온다() throws Exception {
		writeId(A, "1");
		writeId(A, "2");
		writeId(A, "3");

		String first = list(B, "&limit=2").andExpect(jsonPath("$.messages", hasSize(2)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat((List<String>) JsonPath.read(first, "$.messages[*].content")).containsExactly("3", "2");
		String cursor = JsonPath.read(first, "$.nextCursor");

		// 서버가 준 커서(마이크로초)를 그대로 돌려보낸다. get(url) 에 넣으면 MockMvc 가 한 번 더 인코딩하므로 param 으로
		mvc.perform(get("/messages").param("lat", String.valueOf(LAT))
			.param("lon", String.valueOf(LON))
			.param("radius", "300")
			.param("limit", "2")
			.param("cursor", cursor)
			.header(DeviceKeys.HEADER, B))
			.andExpect(jsonPath("$.messages[*].content").value(org.hamcrest.Matchers.contains("1")))
			.andExpect(jsonPath("$.nextCursor").value(nullValue()));
	}

	@Test
	void 마지막_페이지가_딱_맞으면_다음_커서는_null() throws Exception {
		writeId(A, "1");
		writeId(A, "2");

		list(B, "&limit=2").andExpect(jsonPath("$.messages", hasSize(2)))
			.andExpect(jsonPath("$.nextCursor").value(nullValue()));
	}

	@Test
	void 표식은_같은_구간에서_같은_사람이면_유지하고_아니면_비어_있는_번호() throws Exception {
		write(A, "a1", 0, null).andExpect(jsonPath("$.marker").value(0));
		write(B, "b1", 0, null).andExpect(jsonPath("$.marker").value(1));
		write(A, "a2", 0, null).andExpect(jsonPath("$.marker").value(0));
		write(C, "c1", 0, null).andExpect(jsonPath("$.marker").value(2));
	}

	@Test
	void 작성_응답은_목록_항목과_같은_형태() throws Exception {
		long original = writeId(B, "원글");
		write(A, "답글", 0, original).andExpect(status().isCreated())
			.andExpect(jsonPath("$.content").value("답글"))
			.andExpect(jsonPath("$.isMine").value(true))
			.andExpect(jsonPath("$.replyTo.id").value(original))
			.andExpect(jsonPath("$.replyTo.content").value("원글"));
	}

	@Test
	void 금지_구역이면_403() throws Exception {
		jdbc.update("INSERT INTO banned_area (center, radius_m, reason) VALUES (ST_MakePoint(?, ?)::geography, 200, 'test')",
				LON, LAT);

		write(A, "여기", 0, null).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BANNED_AREA"));
	}

	@Test
	void 잘못된_요청은_400() throws Exception {
		write(A, "x".repeat(501), 0, null).andExpect(status().isBadRequest());
		write(A, " ", 0, null).andExpect(status().isBadRequest());
		mvc.perform(get("/messages?lat=%s&lon=%s&radius=5000".formatted(LAT, LON)).header(DeviceKeys.HEADER, A))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(get("/messages?lon=%s&radius=300".formatted(LON)).header(DeviceKeys.HEADER, A))
			.andExpect(status().isBadRequest());
		mvc.perform(delete("/messages/abc").header(DeviceKeys.HEADER, A)).andExpect(status().isBadRequest());
	}

	@Test
	void 기기_키가_없으면_401() throws Exception {
		mvc.perform(get("/messages?lat=%s&lon=%s&radius=300".formatted(LAT, LON)))
			.andExpect(status().isUnauthorized());
	}

	// ── 지우기 ────────────────────────────────────────────

	@Test
	void 내_글만_지울_수_있고_지우면_인용이_비워진다() throws Exception {
		long mine = writeId(A, "지울 글");
		writeId(B, "인용", mine);

		mvc.perform(delete("/messages/" + mine).header(DeviceKeys.HEADER, B))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_OWNER"));
		mvc.perform(delete("/messages/" + mine).header(DeviceKeys.HEADER, A)).andExpect(status().isNoContent());
		mvc.perform(delete("/messages/" + mine).header(DeviceKeys.HEADER, A)).andExpect(status().isNotFound());

		list(C).andExpect(jsonPath("$.messages[*].content").value(org.hamcrest.Matchers.contains("인용")))
			.andExpect(jsonPath("$.messages[0].replyTo.id").value(mine))
			.andExpect(jsonPath("$.messages[0].replyTo.content").value(nullValue()));
	}

	@Test
	void 지운_글에는_답글을_달_수_없다() throws Exception {
		long gone = writeId(A, "곧 지움");
		mvc.perform(delete("/messages/" + gone).header(DeviceKeys.HEADER, A));

		write(B, "늦은 답글", 0, gone).andExpect(status().isNotFound());
	}

	// ── 신고 ──────────────────────────────────────────────

	@Test
	void 신고하면_나에게서만_사라지고_중복_신고는_409() throws Exception {
		long id = writeId(A, "문제 글");

		json(B, post("/messages/" + id + "/report"), "{ \"reason\": \"abuse\" }").andExpect(status().isNoContent());
		json(B, post("/messages/" + id + "/report"), "{ \"reason\": \"spam\" }").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_REPORTED"));
		json(B, post("/messages/" + id + "/report"), "{ \"reason\": \"nope\" }").andExpect(status().isBadRequest());

		assertThat(contents(B)).isEmpty();
		assertThat(contents(C)).containsExactly("문제 글");
	}

	// ── 차단 ──────────────────────────────────────────────

	@Test
	void 차단하면_그_작성자의_글이_모두_사라지고_해제하면_돌아온다() throws Exception {
		long target = writeId(B, "차단할 글");
		writeId(B, "같은 사람 다른 글");
		writeId(C, "다른 사람");

		json(A, post("/blocks"), "{ \"messageId\": %d }".formatted(target)).andExpect(status().isNoContent());
		json(A, post("/blocks"), "{ \"messageId\": %d }".formatted(target)).andExpect(status().isNoContent()); // 다시 해도 그대로

		assertThat(contents(A)).containsExactly("다른 사람");
		assertThat(contents(C)).hasSize(3);

		String blocks = mvc.perform(get("/blocks").header(DeviceKeys.HEADER, A))
			.andExpect(jsonPath("$.blocks", hasSize(1)))
			.andExpect(jsonPath("$.blocks[0].messageContent").value("차단할 글"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(blocks).doesNotContain(DeviceKeys.toAuthorKey(B));
		long blockId = ((Number) JsonPath.read(blocks, "$.blocks[0].id")).longValue();

		mvc.perform(delete("/blocks/" + blockId).header(DeviceKeys.HEADER, C)).andExpect(status().isNotFound());
		mvc.perform(delete("/blocks/" + blockId).header(DeviceKeys.HEADER, A)).andExpect(status().isNoContent());

		assertThat(contents(A)).hasSize(3);
	}

	@Test
	void 차단할_때_지목한_글이_지워지면_목록에서_내용이_비워진다() throws Exception {
		long target = writeId(B, "곧 지울 글");
		json(A, post("/blocks"), "{ \"messageId\": %d }".formatted(target));
		mvc.perform(delete("/messages/" + target).header(DeviceKeys.HEADER, B));

		mvc.perform(get("/blocks").header(DeviceKeys.HEADER, A))
			.andExpect(jsonPath("$.blocks[0].messageContent").value(nullValue()));
	}

	@Test
	void 나를_차단할_수는_없다() throws Exception {
		long mine = writeId(A, "내 글");
		json(A, post("/blocks"), "{ \"messageId\": %d }".formatted(mine)).andExpect(status().isBadRequest());
	}

	@Test
	void 차단한_작성자가_인용된_글은_인용_내용이_비워진다() throws Exception {
		long original = writeId(B, "차단될 사람의 원글");
		writeId(C, "그걸 인용", original);
		json(A, post("/blocks"), "{ \"messageId\": %d }".formatted(original));

		list(A).andExpect(jsonPath("$.messages[0].content").value("그걸 인용"))
			.andExpect(jsonPath("$.messages[0].replyTo.content").value(nullValue()));
	}

	// ── 답글 ──────────────────────────────────────────────

	@Test
	void 내_글에_달린_남의_답글만_최신순으로() throws Exception {
		long mine = writeId(A, "1년 전 사람들 아직 여기 오나");
		writeId(B, "저 왔어요", mine);
		writeId(A, "내가 단 답글", mine);
		long blockedReply = writeId(C, "차단할 사람 답글", mine);
		writeId(B, "두 번째 답글", mine);
		json(A, post("/blocks"), "{ \"messageId\": %d }".formatted(blockedReply));

		mvc.perform(get("/replies").header(DeviceKeys.HEADER, A))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.replies[*].content").value(org.hamcrest.Matchers.contains("두 번째 답글", "저 왔어요")))
			.andExpect(jsonPath("$.replies[0].myMessage.id").value(mine))
			.andExpect(jsonPath("$.replies[0].myMessage.content").value("1년 전 사람들 아직 여기 오나"))
			.andExpect(jsonPath("$.replies[0].marker").isNumber());

		mvc.perform(get("/replies").header(DeviceKeys.HEADER, B)).andExpect(jsonPath("$.replies", hasSize(0)));
	}
}
