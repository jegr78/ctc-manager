package org.ctc.discord.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.SocketTimeoutException;
import org.ctc.discord.exception.DiscordApiException.Category;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientResponseException;

/**
 * Unit tests for DiscordApiExceptionMapper. RED phase: this class fails to
 * compile until Task 4 lands the sealed DiscordApiException hierarchy + the
 * 4 permits + DiscordApiExceptionMapper.
 *
 * Mapping table mirrored from 93-RESEARCH.md § Sealed Exception Hierarchy:
 *   401            -> DiscordAuthException
 *   403            -> DiscordAuthException
 *   404            -> DiscordNotFoundException
 *   400 + 30013    -> DiscordCategoryFullException
 *   400 + other    -> DiscordTransientException
 *   500            -> DiscordTransientException
 *   non-RCRE IOE   -> DiscordTransientException
 *   already-mapped -> same instance returned (idempotent)
 */
class DiscordApiExceptionMapperTest {

	@Test
	void givenStatus401_whenFrom_thenReturnsDiscordAuthException() {
		// given
		var rcre = mockRcre(401, "{}");

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from(rcre);

		// then
		assertThat(actual).isInstanceOf(DiscordAuthException.class);
		assertThat(actual.category()).isEqualTo(Category.AUTH);
		assertThat(actual.getMessage()).isEqualTo(DiscordApiExceptionMapper.AUTH_MESSAGE);
	}

	@Test
	void givenStatus403WithoutMissingPermissionsCode_whenFrom_thenReturnsDiscordAuthException() {
		// given — bare 403 (no Discord error code) means real token rejection
		var rcre = mockRcre(403, "{}");

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from(rcre);

		// then
		assertThat(actual).isInstanceOf(DiscordAuthException.class);
		assertThat(actual.category()).isEqualTo(Category.AUTH);
	}

	@Test
	void givenStatus403WithCode50013_whenFrom_thenReturnsDiscordMissingPermissionsException() {
		// given — Discord JSON error code 50013 == "Missing Permissions"
		var rcre = mockRcre(403, "{\"code\":50013,\"message\":\"Missing Permissions\"}");

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from(rcre);

		// then
		assertThat(actual).isInstanceOf(DiscordMissingPermissionsException.class);
		assertThat(actual.category()).isEqualTo(Category.MISSING_PERMISSIONS);
		assertThat(actual.getMessage()).isEqualTo(DiscordApiExceptionMapper.MISSING_PERMISSIONS_MESSAGE);
	}

	@Test
	void givenStatus403WithOtherDiscordCode_whenFrom_thenReturnsDiscordAuthException() {
		// given — any 403 with a Discord code that is NOT 50013 stays AUTH
		var rcre = mockRcre(403, "{\"code\":50001,\"message\":\"Missing Access\"}");

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from(rcre);

		// then
		assertThat(actual).isInstanceOf(DiscordAuthException.class);
		assertThat(actual.category()).isEqualTo(Category.AUTH);
	}

	@Test
	void givenStatus404_whenFrom_thenReturnsDiscordNotFoundException() {
		// given
		var rcre = mockRcre(404, "{}");

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from(rcre);

		// then
		assertThat(actual).isInstanceOf(DiscordNotFoundException.class);
		assertThat(actual.category()).isEqualTo(Category.NOT_FOUND);
		assertThat(actual.getMessage()).isEqualTo(DiscordApiExceptionMapper.NOT_FOUND_MESSAGE);
	}

	@Test
	void givenStatus400WithCode30013_whenFrom_thenReturnsDiscordCategoryFullException() {
		// given — Discord JSON error code 30013 == "Maximum number of channels in category reached"
		var rcre = mockRcre(400, "{\"code\":30013,\"message\":\"Maximum number of channels in category reached\"}");

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from(rcre);

		// then
		assertThat(actual).isInstanceOf(DiscordCategoryFullException.class);
		assertThat(actual.category()).isEqualTo(Category.CATEGORY_FULL);
		assertThat(actual.getMessage()).isEqualTo(DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE);
	}

	@Test
	void givenStatus400WithoutCode30013_whenFrom_thenReturnsDiscordTransientException() {
		// given — any other 400 body falls back to TRANSIENT
		var rcre = mockRcre(400, "{\"code\":50001,\"message\":\"Missing Access\"}");

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from(rcre);

		// then
		assertThat(actual).isInstanceOf(DiscordTransientException.class);
		assertThat(actual.category()).isEqualTo(Category.TRANSIENT);
	}

	@Test
	void givenStatus500_whenFrom_thenReturnsDiscordTransientException() {
		// given
		var rcre = mockRcre(500, "");

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from(rcre);

		// then
		assertThat(actual).isInstanceOf(DiscordTransientException.class);
		assertThat(actual.category()).isEqualTo(Category.TRANSIENT);
		assertThat(actual.getMessage()).isEqualTo(DiscordApiExceptionMapper.TRANSIENT_MESSAGE);
	}

	@Test
	void givenGenericIOException_whenFrom_thenReturnsDiscordTransientException() {
		// given — bare network/socket failures are transient by definition
		IOException e = new SocketTimeoutException("connection reset by peer");

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from(e);

		// then
		assertThat(actual).isInstanceOf(DiscordTransientException.class);
		assertThat(actual.category()).isEqualTo(Category.TRANSIENT);
		assertThat(actual.getCause()).isSameAs(e);
	}

	@Test
	void givenAlreadyMappedException_whenFrom_thenReturnsSameInstance() {
		// given — Mapper must be idempotent: already-typed exception passes through
		DiscordApiException original = new DiscordNotFoundException("already mapped", null);

		// when
		DiscordApiException actual = DiscordApiExceptionMapper.from((IOException) original);

		// then
		assertThat(actual).isSameAs(original);
	}

	@Test
	void givenAllPermits_whenSwitchOnCategory_thenExhaustive() {
		// given — sealed-permits sanity: each permit reports its matching Category
		DiscordApiException transient_ = new DiscordTransientException("t", null);
		DiscordApiException auth = new DiscordAuthException("a", null);
		DiscordApiException missingPermissions = new DiscordMissingPermissionsException("mp", null);
		DiscordApiException notFound = new DiscordNotFoundException("nf", null);
		DiscordApiException categoryFull = new DiscordCategoryFullException("cf", null);

		// when / then
		assertThat(transient_.category()).isEqualTo(Category.TRANSIENT);
		assertThat(auth.category()).isEqualTo(Category.AUTH);
		assertThat(missingPermissions.category()).isEqualTo(Category.MISSING_PERMISSIONS);
		assertThat(notFound.category()).isEqualTo(Category.NOT_FOUND);
		assertThat(categoryFull.category()).isEqualTo(Category.CATEGORY_FULL);
	}

	private RestClientResponseException mockRcre(int statusCode, String body) {
		var rcre = mock(RestClientResponseException.class);
		when(rcre.getStatusCode()).thenReturn(HttpStatusCode.valueOf(statusCode));
		when(rcre.getResponseBodyAsString()).thenReturn(body);
		return rcre;
	}
}
