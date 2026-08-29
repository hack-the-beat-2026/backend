package com.hackathon.gdg.global.storage;

import java.nio.file.Path;
import java.util.List;

public record StoredImages(
		String originalPhotoUrl,
		String characterImageUrl,
		String previewImageUrl,
		List<Path> paths
) {
}
