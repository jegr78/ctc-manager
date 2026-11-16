package org.ctc.admin;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.ctc.backup.lock.ImportLockedWriteRejector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final ImportLockedWriteRejector importLockedWriteRejector;
	private final SeasonWorkspaceInterceptor seasonWorkspaceInterceptor;

	/** Upload folders whose files get a fresh random name on every store and therefore never change content. */
	static final List<String> CATALOG_FOLDERS = List.of("cars", "tracks", "car-images", "track-images");

	@Value("${app.upload-dir:uploads}")
	private String uploadDir;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String root = "file:" + Paths.get(uploadDir).toAbsolutePath().normalize() + "/";
		for (String folder : CATALOG_FOLDERS) {
			registry.addResourceHandler("/uploads/" + folder + "/**")
					.addResourceLocations(root + folder + "/")
					.setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate());
		}
		registry.addResourceHandler("/uploads/**").addResourceLocations(root);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(seasonWorkspaceInterceptor).addPathPatterns("/admin/**");
		registry.addInterceptor(importLockedWriteRejector)
				.addPathPatterns("/admin/**");
	}
}
