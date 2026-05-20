package org.ctc.dataimport.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.List;
import org.ctc.dataimport.exception.GoogleApiException.Category;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GoogleApiExceptionMapper. RED phase: this class fails to compile
 * until Task 2 lands the GoogleApiException hierarchy + GoogleApiExceptionMapper.
 *
 * Mapping table mirrored from 91-RESEARCH.md § Pattern 2:
 *   401            -> AuthGoogleApiException
 *   403 + auth     -> AuthGoogleApiException
 *   403 + other    -> PermissionGoogleApiException
 *   403 + null     -> PermissionGoogleApiException (defensive default)
 *   404            -> NotFoundGoogleApiException
 *   408/429/5xx    -> TransientGoogleApiException
 *   other / unknown-> TransientGoogleApiException
 *   non-Google IOE -> TransientGoogleApiException
 *   GeneralSec.    -> AuthGoogleApiException
 */
class GoogleApiExceptionMapperTest {

	@Test
	void givenGoogleJsonResponseException401_whenFrom_thenReturnsAuthGoogleApiException() {
		// given
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(401);

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then
		assertThat(actual).isInstanceOf(AuthGoogleApiException.class);
		assertThat(actual.category()).isEqualTo(Category.AUTH);
	}

	@Test
	void givenGoogleJsonResponseException403WithAuthReason_whenFrom_thenReturnsAuthGoogleApiException() {
		// given
		var gjre = mockGjreWithReason(403, "authError");

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then
		assertThat(actual).isInstanceOf(AuthGoogleApiException.class);
		assertThat(actual.category()).isEqualTo(Category.AUTH);
	}

	@Test
	void givenGoogleJsonResponseException403WithInvalidCredentialsReason_whenFrom_thenReturnsAuthGoogleApiException() {
		// given
		var gjre = mockGjreWithReason(403, "invalidCredentials");

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then
		assertThat(actual).isInstanceOf(AuthGoogleApiException.class);
	}

	@Test
	void givenGoogleJsonResponseException403WithForbiddenReason_whenFrom_thenReturnsPermissionGoogleApiException() {
		// given
		var gjre = mockGjreWithReason(403, "forbidden");

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then
		assertThat(actual).isInstanceOf(PermissionGoogleApiException.class);
		assertThat(actual.category()).isEqualTo(Category.PERMISSION);
	}

	@Test
	void givenGoogleJsonResponseException403WithNullDetails_whenFrom_thenReturnsPermissionGoogleApiException() {
		// given — defensive default for 403 with no error-details payload
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(403);
		when(gjre.getDetails()).thenReturn(null);

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then
		assertThat(actual).isInstanceOf(PermissionGoogleApiException.class);
	}

	@Test
	void givenGoogleJsonResponseException404_whenFrom_thenReturnsNotFoundGoogleApiException() {
		// given
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(404);

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then
		assertThat(actual).isInstanceOf(NotFoundGoogleApiException.class);
		assertThat(actual.category()).isEqualTo(Category.NOT_FOUND);
	}

	@Test
	void givenGoogleJsonResponseException500_whenFrom_thenReturnsTransientGoogleApiException() {
		// given
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(500);

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then
		assertThat(actual).isInstanceOf(TransientGoogleApiException.class);
		assertThat(actual.category()).isEqualTo(Category.TRANSIENT);
	}

	@Test
	void givenGoogleJsonResponseException429_whenFrom_thenReturnsTransientGoogleApiException() {
		// given — rate-limit response should be transient
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(429);

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then
		assertThat(actual).isInstanceOf(TransientGoogleApiException.class);
	}

	@Test
	void givenGoogleJsonResponseException503_whenFrom_thenReturnsTransientGoogleApiException() {
		// given — 503 service unavailable
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(503);

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then
		assertThat(actual).isInstanceOf(TransientGoogleApiException.class);
	}

	@Test
	void givenGoogleJsonResponseExceptionUnknownStatus_whenFrom_thenReturnsTransientGoogleApiException() {
		// given — lenient default for any unrecognised status
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(418);

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(gjre);

		// then — default for unrecognised status is transient
		assertThat(actual).isInstanceOf(TransientGoogleApiException.class);
	}

	@Test
	void givenNonGoogleIOException_whenFrom_thenReturnsTransientGoogleApiException() {
		// given — bare network/socket failures are transient by definition
		IOException e = new SocketTimeoutException("connection reset by peer");

		// when
		GoogleApiException actual = GoogleApiExceptionMapper.from(e);

		// then
		assertThat(actual).isInstanceOf(TransientGoogleApiException.class);
		assertThat(actual.category()).isEqualTo(Category.TRANSIENT);
		assertThat(actual.getCause()).isSameAs(e);
	}

	@Test
	void givenGeneralSecurityException_whenFrom_thenReturnsAuthGoogleApiException() {
		// given — credentials file unreadable / signature failure
		GeneralSecurityException e = new GeneralSecurityException("invalid key signature");

		// when
		AuthGoogleApiException actual = GoogleApiExceptionMapper.from(e);

		// then
		assertThat(actual.category()).isEqualTo(Category.AUTH);
		assertThat(actual.getCause()).isSameAs(e);
	}

	@Test
	void givenSealedHierarchy_whenExhaustiveSwitch_thenCompilesForAllFourCategories() {
		// given — sanity check that all 4 typed subtypes share the sealed base
		GoogleApiException auth = new AuthGoogleApiException("a", null);
		GoogleApiException notFound = new NotFoundGoogleApiException("nf", null);
		GoogleApiException perm = new PermissionGoogleApiException("p", null);
		GoogleApiException transient_ = new TransientGoogleApiException("t", null);

		// when / then — every subtype reports its own category
		assertThat(auth.category()).isEqualTo(Category.AUTH);
		assertThat(notFound.category()).isEqualTo(Category.NOT_FOUND);
		assertThat(perm.category()).isEqualTo(Category.PERMISSION);
		assertThat(transient_.category()).isEqualTo(Category.TRANSIENT);
	}

	private GoogleJsonResponseException mockGjreWithReason(int statusCode, String reason) {
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(statusCode);
		var details = mock(GoogleJsonError.class);
		var errorInfo = new GoogleJsonError.ErrorInfo();
		errorInfo.setReason(reason);
		when(details.getErrors()).thenReturn(List.of(errorInfo));
		when(gjre.getDetails()).thenReturn(details);
		return gjre;
	}
}
