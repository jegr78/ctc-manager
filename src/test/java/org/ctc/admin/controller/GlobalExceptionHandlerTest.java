package org.ctc.admin.controller;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

	@Mock
	Environment environment;

	GlobalExceptionHandler handler;

	@BeforeEach
	void setUp() {
		handler = new GlobalExceptionHandler(environment);
	}

	@Test
	void givenEntityNotFound_whenHandled_thenReturns404WithAdminErrorView() {
		// given
		var ex = new EntityNotFoundException("Season", 42L);

		// when
		var mav = handler.handleEntityNotFound(ex);

		// then
		assertThat(mav.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(mav.getViewName()).isEqualTo("admin/error");
		assertThat(mav.getModel().get("status")).isEqualTo(404);
		assertThat(mav.getModel().get("error")).isEqualTo("Not Found");
		assertThat(mav.getModel().get("message")).isEqualTo("Season not found with id: 42");
	}

	@Test
	void givenNoSuchElement_whenHandled_thenReturns404() {
		// given
		var ex = new NoSuchElementException("No value present");

		// when
		var mav = handler.handleNoSuchElement(ex);

		// then
		assertThat(mav.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(mav.getViewName()).isEqualTo("admin/error");
		assertThat(mav.getModel().get("status")).isEqualTo(404);
		assertThat(mav.getModel().get("error")).isEqualTo("Not Found");
	}

	@Test
	void givenValidationException_whenHandled_thenReturns400() {
		// given
		var ex = new ValidationException("Name is required");

		// when
		var mav = handler.handleValidation(ex);

		// then
		assertThat(mav.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(mav.getViewName()).isEqualTo("admin/error");
		assertThat(mav.getModel().get("status")).isEqualTo(400);
		assertThat(mav.getModel().get("error")).isEqualTo("Validation Error");
		assertThat(mav.getModel().get("message")).isEqualTo("Name is required");
	}

	@Test
	void givenBusinessRuleException_whenHandled_thenReturns409() {
		// given
		var ex = new BusinessRuleException("Cannot delete active season");

		// when
		var mav = handler.handleBusinessRule(ex);

		// then
		assertThat(mav.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(mav.getViewName()).isEqualTo("admin/error");
		assertThat(mav.getModel().get("status")).isEqualTo(409);
		assertThat(mav.getModel().get("error")).isEqualTo("Business Rule Violation");
		assertThat(mav.getModel().get("message")).isEqualTo("Cannot delete active season");
	}

	@Test
	void givenGenericException_whenHandled_thenReturns500() {
		// given
		var ex = new RuntimeException("Something broke");

		// when
		var mav = handler.handleGeneral(ex);

		// then
		assertThat(mav.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(mav.getViewName()).isEqualTo("admin/error");
		assertThat(mav.getModel().get("status")).isEqualTo(500);
		assertThat(mav.getModel().get("error")).isEqualTo("Internal Error");
	}

	@Test
	void givenResponseStatusException_whenHandled_thenRethrown() {
		// given
		var ex = new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate label");

		// when / then
		assertThatThrownBy(() -> handler.handleResponseStatus(ex))
				.isSameAs(ex);
	}

	@Test
	void givenDevProfile_whenException_thenShowDetailsTrue() {
		// given
		when(environment.matchesProfiles("dev")).thenReturn(true);
		var ex = new EntityNotFoundException("Season", 42L);

		// when
		var mav = handler.handleEntityNotFound(ex);

		// then
		assertThat(mav.getModel().get("showDetails")).isEqualTo(true);
		assertThat(mav.getModel().get("exceptionType")).isEqualTo("EntityNotFoundException");
	}

	@Test
	void givenProdProfile_whenException_thenShowDetailsFalse() {
		// given
		when(environment.matchesProfiles("dev")).thenReturn(false);
		var ex = new EntityNotFoundException("Season", 42L);

		// when
		var mav = handler.handleEntityNotFound(ex);

		// then
		assertThat(mav.getModel().get("showDetails")).isEqualTo(false);
		assertThat(mav.getModel()).doesNotContainKey("exceptionType");
	}
}
