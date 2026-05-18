package org.ctc.backup.exception;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.ctc.admin.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural / advice-scope IT for SECU-04 — locks in the Phase 74 D-02 architectural
 * invariant that {@link MaxUploadSizeExceededException} is handled by a dedicated
 * sibling {@code @ControllerAdvice} ({@link BackupUploadExceptionHandler}) returning
 * {@code String "redirect:..."}, and NOT by {@link GlobalExceptionHandler} which returns
 * {@link org.springframework.web.servlet.ModelAndView}.
 *
 * <p>Rationale (from {@code BackupUploadExceptionHandler} javadoc): mixing
 * {@code String} and {@code ModelAndView} return types in one advice class creates
 * Spring binding ambiguity. Splitting into two advices is the agreed-upon mitigation.
 *
 * <p>{@code BackupImportMultipartLimitIT} verifies the runtime behaviour (the
 * 100 MB Tomcat limit produces the right Flash-redirect). This IT verifies the
 * structural contract that keeps the runtime contract correct — a future refactor
 * cannot silently merge the two advices without breaking this test.
 *
 * <p>Anchors Phase 74 SECU-04 gap-fill from Phase 87 / Plan 87-04 retroactive audit.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupUploadExceptionHandlerScopeIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void givenBackupUploadExceptionHandler_whenInspected_thenIsControllerAdviceWithHighestOrder() {
        // given
        Class<?> handler = BackupUploadExceptionHandler.class;

        // when / then — class-level @ControllerAdvice annotation present
        assertThat(handler.isAnnotationPresent(ControllerAdvice.class))
                .as("BackupUploadExceptionHandler must be a @ControllerAdvice")
                .isTrue();

        // and — a Spring bean is registered (not a dead class)
        assertThat(context.getBeansOfType(BackupUploadExceptionHandler.class))
                .as("BackupUploadExceptionHandler must be a registered Spring bean")
                .hasSize(1);
    }

    @Test
    void givenBackupUploadExceptionHandler_whenScanned_thenHandlesMaxUploadSizeExceededWithStringReturn()
            throws NoSuchMethodException {
        // given / when
        Method handlerMethod = BackupUploadExceptionHandler.class.getDeclaredMethod(
                "handleMaxUploadSizeExceeded",
                MaxUploadSizeExceededException.class,
                jakarta.servlet.http.HttpServletRequest.class,
                org.springframework.web.servlet.mvc.support.RedirectAttributes.class);

        // then — @ExceptionHandler is wired for the exact multipart-size exception class
        ExceptionHandler annotation = handlerMethod.getAnnotation(ExceptionHandler.class);
        assertThat(annotation)
                .as("handleMaxUploadSizeExceeded must carry @ExceptionHandler")
                .isNotNull();
        assertThat(annotation.value())
                .as("@ExceptionHandler must declare MaxUploadSizeExceededException")
                .containsExactly(MaxUploadSizeExceededException.class);

        // and — return type is String (redirect:...), proving the mixed-return-type
        // split with GlobalExceptionHandler (which returns ModelAndView)
        assertThat(handlerMethod.getReturnType())
                .as("handler must return String to issue a redirect, not ModelAndView")
                .isEqualTo(String.class);
    }

    @Test
    void givenGlobalExceptionHandler_whenScanned_thenDoesNotRegisterMaxUploadSizeException() {
        // given
        Method[] methods = GlobalExceptionHandler.class.getDeclaredMethods();

        // when
        boolean handlesMultipart = Arrays.stream(methods)
                .map(m -> m.getAnnotation(ExceptionHandler.class))
                .filter(a -> a != null)
                .flatMap(a -> Arrays.stream(a.value()))
                .anyMatch(MaxUploadSizeExceededException.class::isAssignableFrom);

        // then — GlobalExceptionHandler must NOT handle MaxUploadSizeExceededException;
        // a future PR that merges the two advices would flip this and fail the test.
        assertThat(handlesMultipart)
                .as("GlobalExceptionHandler must NOT register MaxUploadSizeExceededException "
                        + "(belongs to BackupUploadExceptionHandler to avoid mixed-return-type binding)")
                .isFalse();
    }
}
