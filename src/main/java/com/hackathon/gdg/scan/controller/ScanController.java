package com.hackathon.gdg.scan.controller;

import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.scan.dto.FoundCharacterResponse;
import com.hackathon.gdg.scan.dto.QrCharacterResponse;
import com.hackathon.gdg.scan.service.ScanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ScanController {

	private final ScanService scanService;

	public ScanController(ScanService scanService) {
		this.scanService = scanService;
	}

	@GetMapping("/characters/qr/{qrToken}")
	public QrCharacterResponse lookup(
			@PathVariable String qrToken,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return scanService.lookup(qrToken, actor);
	}

	@PostMapping("/games/{gameId}/characters/{qrToken}/found")
	public FoundCharacterResponse markFound(
			@PathVariable Long gameId,
			@PathVariable String qrToken,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return scanService.markFound(gameId, qrToken, actor);
	}
}
