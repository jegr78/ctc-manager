package org.ctc.backup.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Shared {@code executedBy} resolver for audit rows.
 *
 * <p>Encapsulates the 4-branch resolution logic shared by {@link BackupImportService}
 * and {@link DataImportAuditService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupExecutedByResolver {

    private final Environment environment;

    /**
     * Resolves the {@code executedBy} string for audit rows.
     *
     * <ol>
     *   <li>dev/local profile → literal {@code "dev"}</li>
     *   <li>callerOverride non-blank → callerOverride</li>
     *   <li>SecurityContext authentication name (non-blank) → auth.getName()</li>
     *   <li>fallback → {@code "unknown"}</li>
     * </ol>
     *
     * @param callerOverride optional override value; {@code null} or blank values are ignored
     * @return the resolved executedBy string; never {@code null}
     */
    public String resolve(String callerOverride) {
        if (environment.matchesProfiles("dev | local")) {
            return "dev";
        }
        if (callerOverride != null && !callerOverride.isBlank()) {
            return callerOverride;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "unknown";
    }
}
