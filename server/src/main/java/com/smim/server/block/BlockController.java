package com.smim.server.block;

import com.smim.server.common.DeviceKeys;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlockController {

	private final BlockService service;

	public BlockController(BlockService service) {
		this.service = service;
	}

	@PostMapping("/blocks")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void block(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey,
			@Valid @RequestBody BlockRequest req) {
		service.block(DeviceKeys.toAuthorKey(deviceKey), req.messageId());
	}

	@GetMapping("/blocks")
	public Map<String, List<BlockResponse>> list(
			@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey) {
		return Map.of("blocks", service.list(DeviceKeys.toAuthorKey(deviceKey)));
	}

	@DeleteMapping("/blocks/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unblock(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey,
			@PathVariable long id) {
		service.unblock(DeviceKeys.toAuthorKey(deviceKey), id);
	}
}
