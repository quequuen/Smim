package com.smim.server.presence;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smim.server.common.DeviceKeys;
import com.smim.server.common.GlobalExceptionHandler;
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

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PresenceController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.build();

	@Test
	void 좌표를_받으면_204() throws Exception {
		mvc.perform(post("/presence").header(DeviceKeys.HEADER, DEVICE_KEY)
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY)).andExpect(status().isNoContent());
	}

	@Test
	void 기기_키가_없으면_401() throws Exception {
		mvc.perform(post("/presence").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_DEVICE_KEY"));
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
