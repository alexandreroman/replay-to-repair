package io.temporal.demos.replaytorepair.backend.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal status endpoint, exposed under {@code /api} so it is reachable
 * through the Caddy gateway. Business endpoints (submit issue, list triage
 * workflows) are added in the implementation phase.
 */
@RestController
@RequestMapping("/api")
class StatusController {

	@GetMapping("/status")
	Status status() {
		return new Status("ok", "backend");
	}

	record Status(String status, String service) {
	}

}
