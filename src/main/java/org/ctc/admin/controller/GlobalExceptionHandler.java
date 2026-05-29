package org.ctc.admin.controller;

import java.util.NoSuchElementException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final Environment environment;

	public GlobalExceptionHandler(Environment environment) {
		this.environment = environment;
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ModelAndView handleEntityNotFound(EntityNotFoundException ex) {
		log.warn("Entity not found: {}", ex.getMessage());
		return buildErrorView(HttpStatus.NOT_FOUND, "Not Found", ex);
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ModelAndView handleNoSuchElement(NoSuchElementException ex) {
		log.warn("Element not found: {}", ex.getMessage());
		return buildErrorView(HttpStatus.NOT_FOUND, "Not Found", ex);
	}

	@ExceptionHandler(ValidationException.class)
	public ModelAndView handleValidation(ValidationException ex) {
		log.warn("Validation error: {}", ex.getMessage());
		return buildErrorView(HttpStatus.BAD_REQUEST, "Validation Error", ex);
	}

	@ExceptionHandler(BusinessRuleException.class)
	public ModelAndView handleBusinessRule(BusinessRuleException ex) {
		log.warn("Business rule violation: {}", ex.getMessage());
		return buildErrorView(HttpStatus.CONFLICT, "Business Rule Violation", ex);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ModelAndView handleNoResourceFound(NoResourceFoundException ex) {
		log.debug("No static resource: {}", ex.getResourcePath());
		return buildErrorView(HttpStatus.NOT_FOUND, "Not Found", ex);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseStatusException handleResponseStatus(ResponseStatusException ex) throws ResponseStatusException {
		throw ex;
	}

	@ExceptionHandler(Exception.class)
	public ModelAndView handleGeneral(Exception ex) {
		log.error("Unhandled exception", ex);
		return buildErrorView(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error", ex);
	}

	private ModelAndView buildErrorView(HttpStatus status, String title, Exception ex) {
		var mav = new ModelAndView("admin/error");
		mav.setStatus(status);
		mav.addObject("status", status.value());
		mav.addObject("error", title);
		mav.addObject("message", ex.getMessage());

		boolean showDetails = environment.matchesProfiles("dev");
		mav.addObject("showDetails", showDetails);
		if (showDetails) {
			mav.addObject("exceptionType", ex.getClass().getSimpleName());
		}

		return mav;
	}
}
