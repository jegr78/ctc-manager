package org.ctc.domain.service;

import org.ctc.admin.dto.DriverForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.PsnAliasRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private PsnAliasRepository psnAliasRepository;

    @Mock
    private SeasonDriverRepository seasonDriverRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private TeamRepository teamRepository;

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

    @Nested
    class FindAllTest {

        @Test
        void whenFindAll_thenReturnsAllDrivers() {
            // given
            var driver1 = new Driver("PSN1", "Nick1");
            var driver2 = new Driver("PSN2", "Nick2");
            when(driverRepository.findAll()).thenReturn(List.of(driver1, driver2));

            // when
            var result = driverService.findAll();

            // then
            assertThat(result).hasSize(2);
            assertThat(result).containsExactly(driver1, driver2);
        }
    }

    @Nested
    class FindByIdTest {

        @Test
        void givenExistingId_whenFindById_thenReturnsDriver() {
            // given
            var id = UUID.randomUUID();
            var driver = new Driver("PSN1", "Nick1");
            driver.setId(id);
            when(driverRepository.findById(id)).thenReturn(Optional.of(driver));

            // when
            var result = driverService.findById(id);

            // then
            assertThat(result).isEqualTo(driver);
        }

        @Test
        void givenNonExistentId_whenFindById_thenThrowsEntityNotFoundException() {
            // given
            var id = UUID.randomUUID();
            when(driverRepository.findById(id)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> driverService.findById(id))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Driver")
                    .hasMessageContaining(id.toString());
        }
    }

    @Nested
    class GetEditFormDataTest {

        @Test
        void givenExistingDriver_whenGetEditFormData_thenReturnsDriverWithSeasonDriversAndAllSeasonsAndAllTeams() {
            // given
            var id = UUID.randomUUID();
            var driver = new Driver("PSN1", "Nick1");
            driver.setId(id);

            var season = new Season();
            season.setId(UUID.randomUUID());
            var team = new Team("Team A", "TA");
            team.setId(UUID.randomUUID());
            var seasonDriver = new SeasonDriver(season, driver, team);

            when(driverRepository.findById(id)).thenReturn(Optional.of(driver));
            when(seasonDriverRepository.findByDriverId(id)).thenReturn(List.of(seasonDriver));
            when(seasonRepository.findAll()).thenReturn(List.of(season));
            when(teamRepository.findAll()).thenReturn(List.of(team));

            // when
            var result = driverService.getEditFormData(id);

            // then
            assertThat(result.driver()).isEqualTo(driver);
            assertThat(result.seasonDrivers()).containsExactly(seasonDriver);
            assertThat(result.allSeasons()).containsExactly(season);
            assertThat(result.allTeams()).containsExactly(team);
        }
    }

    @Nested
    class AssignToSeasonTest {

        @Test
        void givenDriverAndSeasonAndTeam_whenAssignToSeason_thenCreatesSeasonDriver() {
            // given
            var driverId = UUID.randomUUID();
            var seasonId = UUID.randomUUID();
            var teamId = UUID.randomUUID();

            var driver = new Driver("PSN1", "Nick1");
            driver.setId(driverId);
            var season = new Season();
            season.setId(seasonId);
            season.setName("Season 2026");
            var team = new Team("Team A", "TA");
            team.setId(teamId);

            when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
            when(seasonRepository.findById(seasonId)).thenReturn(Optional.of(season));
            when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
            when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonId, driverId)).thenReturn(Optional.empty());

            // when
            var message = driverService.assignToSeason(driverId, seasonId, teamId);

            // then
            verify(seasonDriverRepository).save(any(SeasonDriver.class));
            assertThat(message).contains("PSN1").contains("TA").contains("Season 2026");
        }

        @Test
        void givenDriverAlreadyAssigned_whenAssignToSeason_thenUpdatesExistingSeasonDriver() {
            // given
            var driverId = UUID.randomUUID();
            var seasonId = UUID.randomUUID();
            var teamId = UUID.randomUUID();

            var driver = new Driver("PSN1", "Nick1");
            driver.setId(driverId);
            var season = new Season();
            season.setId(seasonId);
            season.setName("Season 2026");
            var oldTeam = new Team("Old Team", "OT");
            oldTeam.setId(UUID.randomUUID());
            var newTeam = new Team("New Team", "NT");
            newTeam.setId(teamId);

            var existingSd = new SeasonDriver(season, driver, oldTeam);

            when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
            when(seasonRepository.findById(seasonId)).thenReturn(Optional.of(season));
            when(teamRepository.findById(teamId)).thenReturn(Optional.of(newTeam));
            when(seasonDriverRepository.findBySeasonIdAndDriverId(seasonId, driverId)).thenReturn(Optional.of(existingSd));

            // when
            var message = driverService.assignToSeason(driverId, seasonId, teamId);

            // then
            assertThat(existingSd.getTeam()).isEqualTo(newTeam);
            verify(seasonDriverRepository).save(existingSd);
            assertThat(message).contains("PSN1").contains("NT").contains("Season 2026");
        }
    }

    @Nested
    class DeleteTest {

        @Test
        void givenExistingDriver_whenDelete_thenRemoves() {
            // given
            var id = UUID.randomUUID();
            var driver = new Driver("PSN1", "Nick1");
            driver.setId(id);
            when(driverRepository.findById(id)).thenReturn(Optional.of(driver));

            // when
            driverService.delete(id);

            // then
            verify(driverRepository).delete(driver);
            verify(driverRepository).flush();
        }

        @Test
        void givenDriverReferencedBySeason_whenDelete_thenThrowsBusinessRuleException() {
            // given
            var id = UUID.randomUUID();
            var driver = new Driver("PSN1", "Nick1");
            driver.setId(id);
            when(driverRepository.findById(id)).thenReturn(Optional.of(driver));
            doThrow(new DataIntegrityViolationException("FK constraint"))
                    .when(driverRepository).flush();

            // when / then
            assertThatThrownBy(() -> driverService.delete(id))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}
