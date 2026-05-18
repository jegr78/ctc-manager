package org.ctc.backup.lock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Surefire unit tests for {@link ImportLockedWriteRejector}.
 *
 * <p>Pin the whitelist-match contract (CONTEXT D-10): the exempt URL
 * {@code /admin/backup/import-execute} is matched via {@code String.equals(requestURI)},
 * NOT {@code startsWith}. A future refactor toward {@code startsWith} would silently
 * smuggle near-match URIs (e.g. {@code /admin/backup/import-execute-foo}) past the
 * 503 guard while the import lock is held — these tests fail loudly on that regression.
 *
 * <p>Also pins step 1 (non-mutating verbs allow) and step 2 (lock-not-held allow) of
 * the {@code preHandle} decision tree so the entire 4-step matrix is unit-covered without
 * spinning up a Spring context.
 */
class ImportLockedWriteRejectorTest {

    private ImportLockService importLockService;
    private ImportLockedWriteRejector rejector;

    @BeforeEach
    void setUp() {
        importLockService = Mockito.mock(ImportLockService.class);
        rejector = new ImportLockedWriteRejector(importLockService);
    }

    @Test
    void givenLockHeld_whenPostToWhitelistedImportExecute_thenAllowsThrough() throws Exception {
        // given — lock held, request is POST /admin/backup/import-execute (verbatim)
        when(importLockService.isLocked()).thenReturn(true);
        HttpServletRequest req = mockRequest("POST", "/admin/backup/import-execute");
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);

        // when
        boolean allowed = rejector.preHandle(req, res, new Object());

        // then — step 3 whitelist hit: allow through, no 503
        assertThat(allowed).as("whitelisted exact-match URL must pass through").isTrue();
        verify(res, never()).setStatus(anyInt());
    }

    @Test
    void givenLockHeld_whenPostToNearMatchSuffixOfWhitelistedUrl_thenRejectedWith503()
            throws Exception {
        // given — lock held, request is POST /admin/backup/import-execute-anything
        // (a suffix-extended URL that would slip through a startsWith match)
        when(importLockService.isLocked()).thenReturn(true);
        HttpServletRequest req = mockRequest("POST", "/admin/backup/import-execute-anything");
        StringWriter body = new StringWriter();
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(body));

        // when
        boolean allowed = rejector.preHandle(req, res, new Object());

        // then — equals match (D-10) must reject; startsWith match would allow (regression)
        assertThat(allowed)
                .as("near-match suffix /admin/backup/import-execute-anything must NOT pass — equals not startsWith (D-10)")
                .isFalse();
        verify(res, times(1)).setStatus(503);
    }

    @Test
    void givenLockHeld_whenPostToBackupsFakePrefixCollision_thenRejectedWith503() throws Exception {
        // given — lock held, /admin/backups-fake (prefix collision with hypothetical /admin/backups path)
        when(importLockService.isLocked()).thenReturn(true);
        HttpServletRequest req = mockRequest("POST", "/admin/backups-fake");
        StringWriter body = new StringWriter();
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(body));

        // when
        boolean allowed = rejector.preHandle(req, res, new Object());

        // then — prefix-collision URL MUST be rejected (not in whitelist, not the exempt URL)
        assertThat(allowed)
                .as("prefix-collision URL /admin/backups-fake must be rejected with 503")
                .isFalse();
        verify(res, times(1)).setStatus(503);
        assertThat(body.toString())
                .as("503 body must carry the banner-verbatim wording (no string drift)")
                .contains("Backup import in progress — write access is temporarily locked.");
    }

    @Test
    void givenLockHeld_whenGetRequest_thenAllowedThroughStep1() throws Exception {
        // given — lock held BUT request is GET (non-mutating verb)
        when(importLockService.isLocked()).thenReturn(true);
        HttpServletRequest req = mockRequest("GET", "/admin/seasons");
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);

        // when
        boolean allowed = rejector.preHandle(req, res, new Object());

        // then — step 1 short-circuit: non-mutating verb always allowed, regardless of lock state
        assertThat(allowed).as("GET request must always pass — step 1 short-circuit").isTrue();
        verify(res, never()).setStatus(anyInt());
        // isLocked() must not even be queried when verb is GET (step-1 short-circuit)
        verify(importLockService, never()).isLocked();
    }

    @Test
    void givenLockNotHeld_whenPostToAnyAdminUrl_thenAllowedThroughStep2() throws Exception {
        // given — no lock held
        when(importLockService.isLocked()).thenReturn(false);
        HttpServletRequest req = mockRequest("POST", "/admin/teams/save");
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);

        // when
        boolean allowed = rejector.preHandle(req, res, new Object());

        // then — step 2 short-circuit: no lock → allow
        assertThat(allowed).as("POST during no-lock must pass — step 2 short-circuit").isTrue();
        verify(res, never()).setStatus(anyInt());
    }

    @Test
    void givenLockHeld_whenPutToWhitelistedImportExecute_thenAllowedThroughExactMatch() throws Exception {
        // given — lock held; PUT verb on whitelisted URL.
        // NOTE: PUT is a mutating verb (step 1 allows it through), so the decision tree
        // reaches step 3 (whitelist equals match) which still permits the exempt URL.
        when(importLockService.isLocked()).thenReturn(true);
        HttpServletRequest req = mockRequest("PUT", "/admin/backup/import-execute");
        HttpServletResponse res = Mockito.mock(HttpServletResponse.class);

        // when
        boolean allowed = rejector.preHandle(req, res, new Object());

        // then — whitelist match by equals → allow regardless of verb (since verb is mutating)
        assertThat(allowed).as("whitelisted URL must pass even for PUT verb").isTrue();
        verify(res, never()).setStatus(anyInt());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HttpServletRequest mockRequest(String method, String uri) {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getRequestURI()).thenReturn(uri);
        return req;
    }
}
