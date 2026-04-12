package org.ctc.admin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile({"dev", "local"})
public class OpenSecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.anyRequest().permitAll()
				)
				.csrf(csrf -> csrf.disable())
				.headers(headers -> headers
						.frameOptions(frame -> frame.disable())
				);
		return http.build();
	}
}
