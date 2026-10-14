package com.smim.server.message;

import com.smim.server.common.DeviceKeys;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 근거: docs/api.md 3장 */
@RestController
public class MessageController {

	private final MessageService service;

	public MessageController(MessageService service) {
		this.service = service;
	}

	@GetMapping("/messages")
	public MessagePage list(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey,
			@Valid @ModelAttribute MessageQuery query) {
		return service.near(DeviceKeys.toAuthorKey(deviceKey), query);
	}

	@PostMapping("/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public MessageResponse create(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey,
			@Valid @RequestBody CreateMessageRequest req) {
		return service.create(DeviceKeys.toAuthorKey(deviceKey), req);
	}

	@DeleteMapping("/messages/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey,
			@PathVariable long id) {
		service.delete(DeviceKeys.toAuthorKey(deviceKey), id);
	}

	@PostMapping("/messages/{id}/report")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void report(@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey,
			@PathVariable long id, @Valid @RequestBody ReportRequest req) {
		service.report(DeviceKeys.toAuthorKey(deviceKey), id, req.reason());
	}

	@GetMapping("/replies")
	public Map<String, List<ReplyResponse>> replies(
			@RequestHeader(name = DeviceKeys.HEADER, required = false) String deviceKey) {
		return Map.of("replies", service.replies(DeviceKeys.toAuthorKey(deviceKey)));
	}
}
