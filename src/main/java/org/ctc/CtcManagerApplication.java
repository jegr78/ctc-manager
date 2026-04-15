package org.ctc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class CtcManagerApplication {

	static void main(String[] args) {
		SpringApplication.run(CtcManagerApplication.class, args);
	}

}
