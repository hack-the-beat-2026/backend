package com.hackathon.gdg.character.controller;

import com.hackathon.gdg.character.dto.CharacterResponse;
import com.hackathon.gdg.character.dto.CharacterSubmitRequest;
import com.hackathon.gdg.character.service.CharacterService;
import com.hackathon.gdg.global.security.AuthenticatedActor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/games/{gameId}/characters")
public class CharacterController {

	private final CharacterService characterService;

	public CharacterController(CharacterService characterService) {
		this.characterService = characterService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public CharacterResponse submit(
			@PathVariable Long gameId,
			@AuthenticationPrincipal AuthenticatedActor actor,
			@Valid @RequestPart("metadata") CharacterSubmitRequest metadata,
			@RequestPart("originalPhoto") MultipartFile originalPhoto,
			@RequestPart("characterImage") MultipartFile characterImage,
			@RequestPart("previewImage") MultipartFile previewImage
	) {
		return characterService.submit(gameId, actor, metadata, originalPhoto, characterImage, previewImage);
	}

	@GetMapping("/me")
	public CharacterResponse getMine(
			@PathVariable Long gameId,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return characterService.getMine(gameId, actor);
	}

	@GetMapping
	public List<CharacterResponse> getAll(
			@PathVariable Long gameId,
			@AuthenticationPrincipal AuthenticatedActor actor
	) {
		return characterService.getAll(gameId, actor);
	}
}
