package org.ctc.domain.service;

import org.ctc.admin.dto.DriverForm;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.PsnAliasRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private PsnAliasRepository psnAliasRepository;

    @InjectMocks
    private DriverService driverService;

    @Nested
    class SaveTest {

        @Test
        void givenNewDriver_whenSave_thenDriverPersisted() {
            // given
            var form = new DriverForm();
            form.setPsnId("NewDriver_PSN");
            form.setNickname("New Driver");
            form.setActive(true);

            when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = driverService.save(form);

            // then
            assertThat(result.getPsnId()).isEqualTo("NewDriver_PSN");
            assertThat(result.getNickname()).isEqualTo("New Driver");
            assertThat(result.isActive()).isTrue();
        }

        @Test
        void givenExistingDriver_whenSave_thenDriverUpdated() {
            // given
            var id = UUID.randomUUID();
            var existing = new Driver("OldPsn", "Old Nick");
            existing.setId(id);

            var form = new DriverForm();
            form.setId(id);
            form.setPsnId("NewPsn");
            form.setNickname("New Nick");
            form.setActive(false);

            when(driverRepository.findById(id)).thenReturn(Optional.of(existing));
            when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = driverService.save(form);

            // then
            assertThat(result.getPsnId()).isEqualTo("NewPsn");
            assertThat(result.getNickname()).isEqualTo("New Nick");
            assertThat(result.isActive()).isFalse();
        }

        @Test
        void givenNewAliases_whenSave_thenAliasesAdded() {
            // given
            var id = UUID.randomUUID();
            var existing = new Driver("Driver1", "Driver One");
            existing.setId(id);
            existing.setAliases(new ArrayList<>());

            var form = new DriverForm();
            form.setId(id);
            form.setPsnId("Driver1");
            form.setNickname("Driver One");
            form.setActive(true);
            form.setAliases(List.of("OldPsn1", "OldPsn2"));

            when(driverRepository.findById(id)).thenReturn(Optional.of(existing));
            when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = driverService.save(form);

            // then
            assertThat(result.getAliases()).hasSize(2);
            assertThat(result.getAliases()).extracting(PsnAlias::getAlias)
                    .containsExactlyInAnyOrder("OldPsn1", "OldPsn2");
        }

        @Test
        void givenRemovedAlias_whenSave_thenAliasRemoved() {
            // given
            var id = UUID.randomUUID();
            var existing = new Driver("Driver1", "Driver One");
            existing.setId(id);
            var aliasesList = new ArrayList<PsnAlias>();
            aliasesList.add(new PsnAlias(existing, "OldPsn1"));
            aliasesList.add(new PsnAlias(existing, "OldPsn2"));
            existing.setAliases(aliasesList);

            var form = new DriverForm();
            form.setId(id);
            form.setPsnId("Driver1");
            form.setNickname("Driver One");
            form.setActive(true);
            form.setAliases(List.of("OldPsn1"));

            when(driverRepository.findById(id)).thenReturn(Optional.of(existing));
            when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = driverService.save(form);

            // then
            assertThat(result.getAliases()).hasSize(1);
            assertThat(result.getAliases().getFirst().getAlias()).isEqualTo("OldPsn1");
        }

        @Test
        void givenEmptyAliasStrings_whenSave_thenBlankAliasesIgnored() {
            // given
            var form = new DriverForm();
            form.setPsnId("Driver1");
            form.setNickname("Driver One");
            form.setActive(true);
            form.setAliases(List.of("ValidAlias", "", "  "));

            when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = driverService.save(form);

            // then
            assertThat(result.getAliases()).hasSize(1);
            assertThat(result.getAliases().getFirst().getAlias()).isEqualTo("ValidAlias");
        }
    }

    @Nested
    class ValidateAliasesTest {

        @Test
        void givenAliasMatchesExistingPsnId_whenValidate_thenError() {
            // given
            var otherId = UUID.randomUUID();
            var otherDriver = new Driver("ConflictPsn", "Other");
            otherDriver.setId(otherId);

            when(driverRepository.findByPsnIdIgnoreCase("ConflictPsn")).thenReturn(Optional.of(otherDriver));

            // when
            var errors = driverService.validateAliases(UUID.randomUUID(), List.of("ConflictPsn"));

            // then
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst()).contains("ConflictPsn");
        }

        @Test
        void givenAliasMatchesOwnPsnId_whenValidate_thenError() {
            // given
            var driverId = UUID.randomUUID();
            var driver = new Driver("MyPsn", "Me");
            driver.setId(driverId);

            when(driverRepository.findByPsnIdIgnoreCase("MyPsn")).thenReturn(Optional.of(driver));

            // when
            var errors = driverService.validateAliases(driverId, List.of("MyPsn"));

            // then
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst()).contains("MyPsn");
        }

        @Test
        void givenAliasMatchesOtherDriverAlias_whenValidate_thenError() {
            // given
            var otherDriver = new Driver("OtherPsn", "Other");
            otherDriver.setId(UUID.randomUUID());
            var existingAlias = new PsnAlias(otherDriver, "TakenAlias");
            existingAlias.setId(UUID.randomUUID());

            when(driverRepository.findByPsnIdIgnoreCase("TakenAlias")).thenReturn(Optional.empty());
            when(psnAliasRepository.findByAliasIgnoreCase("TakenAlias")).thenReturn(Optional.of(existingAlias));

            // when
            var errors = driverService.validateAliases(UUID.randomUUID(), List.of("TakenAlias"));

            // then
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst()).contains("TakenAlias");
        }

        @Test
        void givenAliasMatchesOwnExistingAlias_whenValidate_thenNoError() {
            // given
            var driverId = UUID.randomUUID();
            var driver = new Driver("MyPsn", "Me");
            driver.setId(driverId);
            var ownAlias = new PsnAlias(driver, "MyOldPsn");
            ownAlias.setId(UUID.randomUUID());

            when(driverRepository.findByPsnIdIgnoreCase("MyOldPsn")).thenReturn(Optional.empty());
            when(psnAliasRepository.findByAliasIgnoreCase("MyOldPsn")).thenReturn(Optional.of(ownAlias));

            // when
            var errors = driverService.validateAliases(driverId, List.of("MyOldPsn"));

            // then
            assertThat(errors).isEmpty();
        }

        @Test
        void givenDuplicateInForm_whenValidate_thenError() {
            // given
            when(driverRepository.findByPsnIdIgnoreCase("DupAlias")).thenReturn(Optional.empty());
            when(psnAliasRepository.findByAliasIgnoreCase("DupAlias")).thenReturn(Optional.empty());

            // when
            var errors = driverService.validateAliases(UUID.randomUUID(), List.of("DupAlias", "DupAlias"));

            // then
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst()).contains("DupAlias");
        }

        @Test
        void givenValidAliases_whenValidate_thenNoErrors() {
            // given
            when(driverRepository.findByPsnIdIgnoreCase("Alias1")).thenReturn(Optional.empty());
            when(driverRepository.findByPsnIdIgnoreCase("Alias2")).thenReturn(Optional.empty());
            when(psnAliasRepository.findByAliasIgnoreCase("Alias1")).thenReturn(Optional.empty());
            when(psnAliasRepository.findByAliasIgnoreCase("Alias2")).thenReturn(Optional.empty());

            // when
            var errors = driverService.validateAliases(UUID.randomUUID(), List.of("Alias1", "Alias2"));

            // then
            assertThat(errors).isEmpty();
        }
    }
}
