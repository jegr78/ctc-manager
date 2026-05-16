package org.ctc.backup.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupExecutedByResolverTest {

    @Mock
    Environment environment;

    BackupExecutedByResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BackupExecutedByResolver(environment);
    }

    @Test
    void givenDevProfile_whenResolve_thenReturnsDev() {
        // given
        when(environment.matchesProfiles("dev | local")).thenReturn(true);
        // when
        String result = resolver.resolve(null);
        // then
        assertThat(result).isEqualTo("dev");
    }

    @Test
    void givenNonDevProfileAndCallerOverride_whenResolve_thenReturnsOverride() {
        // given
        when(environment.matchesProfiles("dev | local")).thenReturn(false);
        // when
        String result = resolver.resolve("admin");
        // then
        assertThat(result).isEqualTo("admin");
    }

    @Test
    void givenNonDevProfileAndNoOverrideAndAuth_whenResolve_thenReturnsAuthName() {
        // given
        when(environment.matchesProfiles("dev | local")).thenReturn(false);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("testuser");
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            // when
            String result = resolver.resolve(null);
            // then
            assertThat(result).isEqualTo("testuser");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void givenNonDevProfileAndNoOverrideAndNoAuth_whenResolve_thenReturnsUnknown() {
        // given
        when(environment.matchesProfiles("dev | local")).thenReturn(false);
        SecurityContextHolder.clearContext();
        // when
        String result = resolver.resolve(null);
        // then
        assertThat(result).isEqualTo("unknown");
    }
}
