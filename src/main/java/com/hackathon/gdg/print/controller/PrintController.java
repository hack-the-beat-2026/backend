package com.hackathon.gdg.print.controller;

import com.hackathon.gdg.global.security.AuthenticatedActor;
import com.hackathon.gdg.print.dto.PrintSheetResponse;
import com.hackathon.gdg.print.service.PrintService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/games/{gameId}")
public class PrintController {

	private final PrintService printService;

	public PrintController(PrintService printService) {
		this.printService = printService;
	}

	@GetMapping("/print-sheet")
	public PrintSheetResponse getPrintSheet(
			@PathVariable Long gameId,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return printService.getPrintSheet(gameId, actor);
	}

	@GetMapping(value = "/characters/{characterId}/qr", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> getQrImage(
			@PathVariable Long gameId,
			@PathVariable Long characterId,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
				.contentType(MediaType.IMAGE_PNG)
				.body(printService.getQrImage(gameId, characterId, actor));
	}
}
