package org.ctc.domain.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.ctc.domain.model.Car;
import org.ctc.domain.repository.CarRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CtcDevSpringBootContext
@Tag("integration")
class FileReplacementIT {
    @Autowired CarService cars;
    @Autowired CarRepository rows;
    @Autowired FileStorageService files;
    @Autowired PlatformTransactionManager transactions;
    @Value("${app.upload-dir}") String uploadDir;
    TransactionTemplate tx;
    UUID id;
    String original;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            var car = rows.save(new Car("Test replacement", UUID.randomUUID().toString()));
            id = car.getId();
            cars.uploadImage(id, image());
            original = car.getImageUrl();
        });
    }

    @AfterEach
    void cleanUp() {
        if (id != null) tx.executeWithoutResult(status -> cars.delete(id));
        files.delete(original);
    }

    @Test
    void givenExistingImage_whenNewFileIsRejected_thenRetainOldFileAndReference() {
        // when / then
        assertThatThrownBy(() -> cars.uploadImage(id,
                new MockMultipartFile("image", "bad.txt", "text/plain", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(path(original)).exists();
        assertThat(rows.findById(id).orElseThrow().getImageUrl()).isEqualTo(original);
    }

    @Test
    void givenExistingImage_whenReplacementTransactionRollsBack_thenRemoveOnlyTheNewFile() {
        // given
        var replacement = new AtomicReference<String>();
        // when
        tx.executeWithoutResult(status -> {
            cars.uploadImage(id, image());
            replacement.set(rows.findById(id).orElseThrow().getImageUrl());
            assertThat(path(original)).exists();
            assertThat(path(replacement.get())).exists();
            status.setRollbackOnly();
        });
        // then
        assertThat(path(original)).exists();
        assertThat(path(replacement.get())).doesNotExist();
        assertThat(rows.findById(id).orElseThrow().getImageUrl()).isEqualTo(original);
    }

    @Test
    void givenExistingImage_whenReplacementCommits_thenRemoveTheOldFileAfterCommit() {
        // when
        tx.executeWithoutResult(status -> {
            cars.uploadImage(id, image());
            assertThat(path(original)).exists();
        });
        // then
        assertThat(Files.exists(path(original))).isFalse();
        assertThat(path(rows.findById(id).orElseThrow().getImageUrl())).exists();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "test.png", "image/png", new byte[]{1, 2, 3});
    }

    private Path path(String url) {
        return Path.of(uploadDir).resolve(url.substring("/uploads/".length()));
    }
}
