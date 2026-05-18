package org.ctc.admin;

import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.ctc.backup.lock.ImportLockedWriteRejector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final ImportLockedWriteRejector importLockedWriteRejector;

	@Value("${app.upload-dir:uploads}")
	private String uploadDir;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/uploads/**")
				.addResourceLocations("file:" + Paths.get(uploadDir).toAbsolutePath().normalize() + "/");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(importLockedWriteRejector)
				.addPathPatterns("/admin/**");
	}
}
