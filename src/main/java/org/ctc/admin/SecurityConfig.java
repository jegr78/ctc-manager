package org.ctc.admin;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile({"prod", "docker"})
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.anyRequest().authenticated()
				)
				.httpBasic(Customizer.withDefaults())
                .headers(headers -> headers
                        .frameOptions(org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig::disable)
                        .addHeaderWriter((request, response) -> response.setHeader("X-Frame-Options",
                                request.getRequestURI().startsWith(request.getContextPath() + "/admin/site-preview/") ? "SAMEORIGIN" : "DENY")))
				.exceptionHandling(exceptions -> exceptions
						.accessDeniedPage("/admin/access-denied")
				);
		return http.build();
	}
}
