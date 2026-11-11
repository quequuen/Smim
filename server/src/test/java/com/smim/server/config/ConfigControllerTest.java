package com.smim.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smim.server.common.DeviceKeys;
import com.smim.server.common.GlobalExceptionHandler;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConfigControllerTest {

	private static final SmimProperties PROPS = props(new SmimProperties.Experiment(
			List.of(new SmimProperties.Group("A", 100, 150, 15), new SmimProperties.Group("B", 150, 225, 20),
					new SmimProperties.Group("C", 300, 450, 45), new SmimProperties.Group("D", 500, 750, 70))));

	/**
	 * /config 는 실험 그룹만 본다. 나머지 설정은 값이 있기만 하면 되므로 한곳에 모아 둔다 —
	 * SmimProperties 에 항목이 늘어도 이 테스트가 깨지지 않게.
	 */
	private static SmimProperties props(SmimProperties.Experiment experiment) {
		return new SmimProperties(experiment, new SmimProperties.Location(45), new SmimProperties.Marker(6, 300),
				new SmimProperties.Moderation(5, Duration.ofMinutes(1), 5, 3, 100, Duration.ofHours(24)),
				new SmimProperties.Admin(null));
	}

	private final MockMvc mvc = mvcWith(PROPS);

	private static MockMvc mvcWith(SmimProperties props) {
		return MockMvcBuilders.standaloneSetup(new ConfigController(props))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	private static String key(int seed) {
		byte[] bytes = new byte[32];
		bytes[0] = (byte) seed;
		bytes[1] = (byte) (seed >> 8);
		return Base64.getEncoder().encodeToString(bytes);
	}

	private String groupOf(MockMvc mvc, String deviceKey) throws Exception {
		String body = mvc.perform(get("/config").header(DeviceKeys.HEADER, deviceKey))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.group");
	}

	@Test
	void 그룹의_반경과_공통값을_camelCase로_내려준다() throws Exception {
		mvc.perform(get("/config").header(DeviceKeys.HEADER, key(0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.group").isString())
			.andExpect(jsonPath("$.radiusM").isNumber())
			.andExpect(jsonPath("$.keepRadiusM").isNumber())
			.andExpect(jsonPath("$.moveThresholdM").isNumber())
			.andExpect(jsonPath("$.heartbeatSec").value(45))
			.andExpect(jsonPath("$.markerWindowHour").value(6));
	}

	@Test
	void 같은_기기는_항상_같은_그룹() throws Exception {
		String first = groupOf(mvc, key(7));
		for (int i = 0; i < 5; i++) {
			assertThat(groupOf(mvc, key(7))).isEqualTo(first);
		}
	}

	@Test
	void 기기가_여러_대면_네_그룹에_고루_퍼진다() throws Exception {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 64; i++) {
			seen.add(groupOf(mvc, key(i)));
		}
		assertThat(seen).containsExactlyInAnyOrder("A", "B", "C", "D");
	}

	@Test
	void 그룹을_하나만_남기면_전원이_그_값을_받는다() throws Exception {
		MockMvc single = mvcWith(
				props(new SmimProperties.Experiment(List.of(new SmimProperties.Group("C", 300, 450, 45)))));
		for (int i = 0; i < 16; i++) {
			assertThat(groupOf(single, key(i))).isEqualTo("C");
		}
	}

	@Test
	void 기기_키가_없으면_401() throws Exception {
		mvc.perform(get("/config"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_DEVICE_KEY"));
	}
}
