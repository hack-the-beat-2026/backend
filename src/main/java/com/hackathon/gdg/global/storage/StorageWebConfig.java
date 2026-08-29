package com.hackathon.gdg.global.storage;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StorageWebConfig implements WebMvcConfigurer {

	private final LocalImageStorage storage;

	public StorageWebConfig(LocalImageStorage storage) {
		this.storage = storage;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String location = storage.root().toUri().toString();
		if (!location.endsWith("/")) {
			location += "/";
		}
		registry.addResourceHandler("/files/**").addResourceLocations(location);
	}
}
