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
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConfigControllerTest {

	private static final SmimProperties PROPS = new SmimProperties(
			new SmimProperties.Experiment(List.of(new SmimProperties.Group("A", 100, 150, 15),
					new SmimProperties.Group("B", 150, 225, 20), new SmimProperties.Group("C", 300, 450, 45),
					new SmimProperties.Group("D", 500, 750, 70))),
			new SmimProperties.Location(45), new SmimProperties.Marker(6));

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
		MockMvc single = mvcWith(new SmimProperties(
				new SmimProperties.Experiment(List.of(new SmimProperties.Group("C", 300, 450, 45))),
				new SmimProperties.Location(45), new SmimProperties.Marker(6)));
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
