package com.smim.server.presence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smim.server.common.DeviceKeys;
import com.smim.server.common.GeoPoint;
import com.smim.server.common.GlobalExceptionHandler;
import com.smim.server.config.TestProps;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PresenceControllerTest {

	private static final String DEVICE_KEY = Base64.getEncoder().encodeToString(new byte[32]);

	private static final String BODY = """
			{ "lat": 37.5563, "lon": 126.9238, "sessionId": "s-1" }
			""";

	private static final int NEARBY_MIN = 3;

	private final PresenceStore store = mock(PresenceStore.class);

	private final MockMvc mvc = mvcWith(NEARBY_MIN);

	private MockMvc mvcWith(int nearbyMin) {
		return MockMvcBuilders
			.standaloneSetup(new PresenceController(store, TestProps.presence(nearbyMin), TestProps.smim()))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void 좌표를_받으면_저장한다() throws Exception {
		mvc.perform(post("/presence").header(DeviceKeys.HEADER, DEVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isOk());

		// 원본 키가 아니라 해시(author_key)가 넘어가야 한다
		verify(store).upsert("s-1", DeviceKeys.toAuthorKey(DEVICE_KEY), new GeoPoint(37.5563, 126.9238));
	}

	@Test
	void 하한을_넘으면_근처_인원을_돌려준다() throws Exception {
		when(store.countAuthorsWithin(any(), anyInt())).thenReturn(6);

		mvc.perform(post("/presence").header(DeviceKeys.HEADER, DEVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isOk()).andExpect(jsonPath("$.nearby").value(6));
	}

	@Test
	void 하한에_정확히_닿으면_보여준다() throws Exception {
		when(store.countAuthorsWithin(any(), anyInt())).thenReturn(NEARBY_MIN);

		mvc.perform(post("/presence").header(DeviceKeys.HEADER, DEVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(jsonPath("$.nearby").value(NEARBY_MIN));
	}

	/**
	 * 혼자 있는데 "2명" 이 뜨면 방금 들어온 특정인의 존재를 알려주는 셈이다.
	 * 앱이 숨기는 것이 아니라 서버가 지워서 보낸다 — 응답에 값이 남아 있으면 앱 한 줄로 드러난다.
	 */
	@Test
	void 하한에_못_미치면_숫자_자체를_내려보내지_않는다() throws Exception {
		when(store.countAuthorsWithin(any(), anyInt())).thenReturn(2);

		mvc.perform(post("/presence").header(DeviceKeys.HEADER, DEVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isOk()).andExpect(jsonPath("$.nearby").doesNotExist());
	}

	@Test
	void 나밖에_없으면_숨긴다() throws Exception {
		when(store.countAuthorsWithin(any(), anyInt())).thenReturn(1);

		mvc.perform(post("/presence").header(DeviceKeys.HEADER, DEVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(jsonPath("$.nearby").doesNotExist());
	}

	/** 헤더가 "이 근처" 라고 말하므로 목록(GET /messages)과 같은 반경으로 세야 한다 */
	@Test
	void 자기_실험_그룹의_반경으로_센다() throws Exception {
		String authorKey = DeviceKeys.toAuthorKey(DEVICE_KEY);
		int radiusM = TestProps.smim().experiment().groupOf(authorKey).radiusM();

		mvc.perform(post("/presence").header(DeviceKeys.HEADER, DEVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isOk());

		verify(store).countAuthorsWithin(new GeoPoint(37.5563, 126.9238), radiusM);
	}

	@Test
	void 하한을_1로_두면_혼자여도_보인다() throws Exception {
		when(store.countAuthorsWithin(any(), anyInt())).thenReturn(1);

		mvcWith(1)
			.perform(post("/presence").header(DeviceKeys.HEADER, DEVICE_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(jsonPath("$.nearby").value(1));
	}

	@Test
	void 기기_키가_없으면_401() throws Exception {
		mvc.perform(post("/presence").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_DEVICE_KEY"));
		verifyNoInteractions(store);
	}

	@Test
	void 기기_키가_32바이트가_아니면_401() throws Exception {
		String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
		mvc.perform(post("/presence").header(DeviceKeys.HEADER, shortKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isUnauthorized());
	}

	@Test
	void 위도가_범위를_벗어나면_400() throws Exception {
		mvc.perform(post("/presence").header(DeviceKeys.HEADER, DEVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "lat": 91, "lon": 126.9238, "sessionId": "s-1" }
					""")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
