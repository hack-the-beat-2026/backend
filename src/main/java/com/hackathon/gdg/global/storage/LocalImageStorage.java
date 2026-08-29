package com.hackathon.gdg.global.storage;

import com.hackathon.gdg.global.error.ApiException;
import com.hackathon.gdg.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class LocalImageStorage {

	private static final Set<String> PHOTO_FORMATS = Set.of("png", "jpeg");
	private static final Set<String> CHARACTER_FORMATS = Set.of("png");
	private static final long MAX_PIXELS = 40_000_000L;

	private final Path root;

	public LocalImageStorage(@Value("${app.storage.root}") String root) {
		this.root = Path.of(root).toAbsolutePath().normalize();
	}

	public StoredImages store(
			Long gameId,
			Long participantId,
			MultipartFile originalPhoto,
			MultipartFile characterImage,
			MultipartFile previewImage
	) {
		List<Path> createdPaths = new ArrayList<>();
		try {
			Path directory = root.resolve(gameId.toString()).resolve(participantId.toString()).normalize();
			if (!directory.startsWith(root)) {
				throw storageError("올바르지 않은 이미지 저장 경로입니다.");
			}
			Files.createDirectories(directory);

			String originalUrl = storeOne(directory, originalPhoto, "original", PHOTO_FORMATS, createdPaths);
			String characterUrl = storeOne(directory, characterImage, "character", CHARACTER_FORMATS, createdPaths);
			String previewUrl = storeOne(directory, previewImage, "preview", PHOTO_FORMATS, createdPaths);
			return new StoredImages(originalUrl, characterUrl, previewUrl, List.copyOf(createdPaths));
		} catch (ApiException exception) {
			delete(createdPaths);
			throw exception;
		} catch (IOException exception) {
			delete(createdPaths);
			throw storageError("이미지를 저장하지 못했습니다.");
		}
	}

	public void delete(StoredImages images) {
		if (images != null) {
			delete(images.paths());
		}
	}

	Path root() {
		return root;
	}

	private String storeOne(
			Path directory,
			MultipartFile file,
			String prefix,
			Set<String> allowedFormats,
			List<Path> createdPaths
	) throws IOException {
		String format = detectFormat(file);
		if (!allowedFormats.contains(format)) {
			throw new ApiException(
					ErrorCode.INVALID_IMAGE,
					HttpStatus.BAD_REQUEST,
					prefix + " 이미지 형식이 올바르지 않습니다."
			);
		}
		String extension = format.equals("jpeg") ? "jpg" : format;
		Path target = directory.resolve(prefix + "-" + UUID.randomUUID() + "." + extension).normalize();
		Files.copy(file.getInputStream(), target);
		createdPaths.add(target);
		return "/files/" + root.relativize(target).toString().replace('\\', '/');
	}

	private String detectFormat(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new ApiException(ErrorCode.INVALID_IMAGE, HttpStatus.BAD_REQUEST, "필수 이미지가 비어 있습니다.");
		}
		try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
			if (input == null) {
				throw invalidImage();
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw invalidImage();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				long pixels = Math.multiplyExact((long) reader.getWidth(0), (long) reader.getHeight(0));
				if (pixels <= 0 || pixels > MAX_PIXELS) {
					throw invalidImage();
				}
				String format = reader.getFormatName().toLowerCase(Locale.ROOT);
				return format.equals("jpg") ? "jpeg" : format;
			} catch (ArithmeticException exception) {
				throw invalidImage();
			} finally {
				reader.dispose();
			}
		}
	}

	private ApiException invalidImage() {
		return new ApiException(ErrorCode.INVALID_IMAGE, HttpStatus.BAD_REQUEST, "PNG 또는 JPEG 이미지가 필요합니다.");
	}

	private ApiException storageError(String message) {
		return new ApiException(ErrorCode.STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
	}

	private void delete(List<Path> paths) {
		for (Path path : paths) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException ignored) {
				// 원래 오류를 유지하고 잔여 파일은 운영 로그/정리 작업에서 처리한다.
			}
		}
	}
}
