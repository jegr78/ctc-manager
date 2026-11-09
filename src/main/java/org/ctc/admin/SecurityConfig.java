package org.ctc.admin;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@Profile({"prod", "docker"})
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers(new AndRequestMatcher(onSeparateManagementPort(environment),
								PathPatternRequestMatcher.withDefaults().matcher("/actuator/prometheus"))).permitAll()
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

	/** Matches requests that arrive on a management port distinct from the application port. */
	static RequestMatcher onSeparateManagementPort(Environment environment) {
		return request -> {
			Integer management = environment.getProperty("local.management.port", Integer.class);
			Integer server = environment.getProperty("local.server.port", Integer.class);
			return management != null && management > 0 && !management.equals(server)
					&& request.getLocalPort() == management;
		};
	}
}
