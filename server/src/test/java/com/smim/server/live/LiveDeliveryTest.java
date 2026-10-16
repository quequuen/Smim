package com.smim.server.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import com.smim.server.common.DeviceKeys;
import com.smim.server.config.SmimProperties;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 실제 서버를 띄우고 STOMP 클라이언트로 붙는다 — 폰 여러 대 대신.
 *
 * 개발 DB 를 같이 쓰므로 바다 위 좌표에서만 쓰고, 그 주변 글과 live- 세션만 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveDeliveryTest {

	// 남태평양 (MessageApiTest 와 다른 곳). 위도 0.001° ≈ 111m
	private static final double LAT = -47.0;

	private static final double LON = -120.0;

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	SmimProperties props;

	private final HttpClient http = HttpClient.newHttpClient();

	private final List<StompSession> opened = new ArrayList<>();

	private WebSocketStompClient stomp;

	private int seed;

	/** 앞 테스트의 연결 종료가 늦게 처리돼도 session-id 가 겹치지 않게 */
	private final String run = Long.toHexString(System.nanoTime());

	private String sid(String name) {
		return name + "-" + run;
	}

	@BeforeEach
	void setUp() {
		stomp = new WebSocketStompClient(new StandardWebSocketClient());
		cleanUp();
	}

	@AfterEach
	void cleanUp() {
		opened.forEach(s -> {
			if (s.isConnected()) {
				s.disconnect();
			}
		});
		opened.clear();
		String area = "ST_DWithin(location, ST_MakePoint(%s, %s)::geography, 50000)".formatted(LON, LAT);
		String ids = "SELECT id FROM message WHERE " + area;
		jdbc.update("DELETE FROM report WHERE message_id IN (" + ids + ")");
		jdbc.update("DELETE FROM user_block WHERE message_id IN (" + ids + ")");
		jdbc.update("UPDATE message SET reply_to_id = NULL WHERE " + area);
		jdbc.update("DELETE FROM message WHERE " + area);
		jdbc.update("DELETE FROM active_session WHERE session_id LIKE 'live-%'");
	}

	// ── 도우미 ─────────────────────────────────────────────

	/** 이 실험 그룹에 배정되는 기기 키. 그룹은 키의 해시로 정해지므로 맞을 때까지 만들어 본다 */
	private String keyIn(String group) {
		while (true) {
			byte[] bytes = new byte[32];
			bytes[0] = 0x6c;
			bytes[1] = (byte) (++seed);
			bytes[2] = (byte) (seed >> 8);
			String key = Base64.getEncoder().encodeToString(bytes);
			if (props.experiment().groupOf(DeviceKeys.toAuthorKey(key)).name().equals(group)) {
				return key;
			}
		}
	}

	private static double north(double meters) {
		return LAT + meters / 111_000;
	}

	record Phone(StompSession session, BlockingQueue<String> inbox) {

		String next() throws InterruptedException {
			return inbox.poll(5, TimeUnit.SECONDS);
		}

		/** 받지 않아야 할 때. 받을 쪽이 이미 받은 뒤에 부르므로 짧게 기다린다 */
		String nothing() throws InterruptedException {
			return inbox.poll(700, TimeUnit.MILLISECONDS);
		}
	}

	private Phone connect(String deviceKey, String sessionId, double lat) throws Exception {
		StompHeaders headers = new StompHeaders();
		headers.add(DeviceKeys.HEADER, deviceKey);
		headers.add("session-id", sid(sessionId));
		headers.add("lat", String.valueOf(lat));
		headers.add("lon", String.valueOf(LON));
		StompSession session = stomp
			.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
					new StompSessionHandlerAdapter() {
					})
			.get(5, TimeUnit.SECONDS);
		opened.add(session);

		BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
		session.subscribe("/user/queue/messages", new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders h) {
				return byte[].class;
			}

			@Override
			public void handleFrame(StompHeaders h, Object payload) {
				inbox.add(new String((byte[]) payload, StandardCharsets.UTF_8));
			}
		});
		Thread.sleep(200); // SUBSCRIBE 는 응답이 없다 — 브로커에 등록될 시간을 준다
		return new Phone(session, inbox);
	}

	private HttpResponse<String> call(String deviceKey, String method, String path, String body) throws Exception {
		HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.header(DeviceKeys.HEADER, deviceKey)
			.header("Content-Type", "application/json");
		req.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
		return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
	}

	private long write(String deviceKey, String content, Long replyToId) throws Exception {
		HttpResponse<String> res = call(deviceKey, "POST", "/messages", """
				{ "content": "%s", "lat": %s, "lon": %s, "replyToId": %s }
				""".formatted(content, LAT, LON, replyToId));
		assertThat(res.statusCode()).isEqualTo(201);
		return ((Number) JsonPath.read(res.body(), "$.id")).longValue();
	}

	// ── 전달 대상 ──────────────────────────────────────────

	@Test
	void 반경_안의_접속자는_받고_반경_밖과_작성자_본인은_받지_않는다() throws Exception {
		String writer = keyIn("C");
		Phone me = connect(writer, "live-writer", LAT);
		Phone near = connect(keyIn("C"), "live-near", north(200));
		Phone far = connect(keyIn("C"), "live-far", north(400));

		long id = write(writer, "여기 지금 사람 많아요?", null);

		String got = near.next();
		assertThat(got).isNotNull();
		assertThat(((Number) JsonPath.read(got, "$.id")).longValue()).isEqualTo(id);
		assertThat((String) JsonPath.read(got, "$.content")).isEqualTo("여기 지금 사람 많아요?");
		assertThat((Boolean) JsonPath.read(got, "$.isMine")).isFalse();
		assertThat((Object) JsonPath.read(got, "$.createdAt")).isInstanceOf(String.class);
		assertThat(got).doesNotContain(DeviceKeys.toAuthorKey(writer), "author", "lat", "lon");

		assertThat(far.nothing()).isNull();
		assertThat(me.nothing()).isNull();
	}

	@Test
	void 수신자의_실험_그룹_반경을_따른다() throws Exception {
		Phone small = connect(keyIn("A"), "live-a", north(200)); // A = 100m → 200m 밖은 안 받는다
		Phone large = connect(keyIn("D"), "live-d", north(400)); // D = 500m → 400m 는 받는다

		write(keyIn("C"), "그룹마다 다르게", null);

		assertThat(large.next()).isNotNull();
		assertThat(small.nothing()).isNull();
	}

	// ── 수신자별 필터 ─────────────────────────────────────

	@Test
	void 작성자를_차단한_사람은_받지_않는다() throws Exception {
		String writer = keyIn("C");
		String blocker = keyIn("C");
		long earlier = write(writer, "예전 글", null);
		assertThat(call(blocker, "POST", "/blocks", "{ \"messageId\": %d }".formatted(earlier)).statusCode())
			.isEqualTo(204);

		Phone blocking = connect(blocker, "live-blocker", LAT);
		Phone other = connect(keyIn("C"), "live-other", LAT);

		write(writer, "새 글", null);

		assertThat(other.next()).isNotNull();
		assertThat(blocking.nothing()).isNull();
	}

	@Test
	void 인용한_원글을_신고한_사람에게는_인용_내용을_비워서_보낸다() throws Exception {
		long original = write(keyIn("C"), "문제 글", null);
		String reporter = keyIn("C");
		assertThat(call(reporter, "POST", "/messages/" + original + "/report", "{ \"reason\": \"abuse\" }")
			.statusCode()).isEqualTo(204);

		Phone reporting = connect(reporter, "live-reporter", LAT);
		Phone other = connect(keyIn("C"), "live-other", LAT);

		write(keyIn("C"), "그걸 인용", original);

		String toReporter = reporting.next();
		String toOther = other.next();
		assertThat((Object) JsonPath.read(toReporter, "$.replyTo.content")).isNull();
		assertThat((String) JsonPath.read(toOther, "$.replyTo.content")).isEqualTo("문제 글");
	}

	// ── 연결 ──────────────────────────────────────────────

	@Test
	void 접속하면_위치가_바로_등록되고_끊으면_지워진다() throws Exception {
		Phone p = connect(keyIn("C"), "live-conn", LAT);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM active_session WHERE session_id = ?", Integer.class,
				sid("live-conn"))).isEqualTo(1);

		p.session().disconnect();
		Thread.sleep(500);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM active_session WHERE session_id = ?", Integer.class,
				sid("live-conn"))).isZero();
	}

	@Test
	void 기기_키가_없으면_접속할_수_없다() {
		assertThatThrownBy(() -> connect("", "live-nokey", LAT)).isNotNull();
	}

	@Test
	void 다른_기기의_session_id_로는_접속할_수_없다() throws Exception {
		connect(keyIn("C"), "live-taken", LAT);
		assertThatThrownBy(() -> connect(keyIn("C"), "live-taken", LAT)).isNotNull();
	}
}
