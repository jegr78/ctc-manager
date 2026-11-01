package org.ctc.domain.service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarServiceTest {

	@Mock
	private CarRepository carRepository;

	@Mock
	private RaceRepository raceRepository;

	@Mock
	private SeasonRepository seasonRepository;

    @Mock private org.ctc.domain.repository.SeasonCarPoolMemberRepository poolMembers;

	@Mock
	private FileStorageService fileStorageService;

	@Spy private CarMetadataService metadataService = new CarMetadataService();

	@InjectMocks
	private CarService carService;


    @Test
    void givenFrozenSnapshotMember_whenDeletingCar_thenRejectBeforeDeletingImageOrCar() {
        // given
        var car = new Car("Fixture", "Member");
        car.setId(UUID.randomUUID());
        car.setImageUrl("/uploads/member.png");
        when(carRepository.findByIdForUpdate(car.getId())).thenReturn(Optional.of(car));
        when(poolMembers.existsByCarId(car.getId())).thenReturn(true);
        // when / then
        assertThatThrownBy(() -> carService.delete(car.getId())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("snapshot");
        verify(carRepository, never()).delete(any());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void givenInvalidManualYear_whenSaving_thenRejectsBeforeAnyRepositoryMutation() {
        // given
        var metadata = new CarMetadataService.ManualMetadata(null, null, 99, false, false, false);
        // when / then
        assertThatThrownBy(() -> carService.save(UUID.randomUUID(), "Test", "Test Car", metadata))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("four-digit");
        verifyNoInteractions(carRepository);
    }

	@Nested
	class FindAllSortedTest {

		@Test
		void whenFindAllSorted_thenDelegatesToRepository() {
			// given
			var cars = List.of(new Car("Honda", "NSX"), new Car("Toyota", "Supra"));
			when(carRepository.findAllByOrderByManufacturerAscNameAsc()).thenReturn(cars);

			// when
			var result = carService.findAllSorted();

			// then
			assertThat(result).hasSize(2);
			verify(carRepository).findAllByOrderByManufacturerAscNameAsc();
		}
	}

	@Nested
	class FindByIdTest {

		@Test
		void givenExistingId_whenFindById_thenReturnsCar() {
			// given
			var id = UUID.randomUUID();
			var car = new Car("Honda", "NSX");
			car.setId(id);
			when(carRepository.findById(id)).thenReturn(Optional.of(car));

			// when
			var result = carService.findById(id);

			// then
			assertThat(result.getName()).isEqualTo("NSX");
		}

		@Test
		void givenNonExistentId_whenFindById_thenThrowsEntityNotFoundException() {
			// given
			var id = UUID.randomUUID();
			when(carRepository.findById(id)).thenReturn(Optional.empty());

			// when / then
			assertThatThrownBy(() -> carService.findById(id))
					.isInstanceOf(EntityNotFoundException.class)
					.hasMessageContaining("Car");
		}
	}

	@Nested
	class SaveTest {

		@Test
		void givenNewCar_whenSave_thenCreatesCar() {
			// given
			when(carRepository.saveAndFlush(any(Car.class))).thenAnswer(inv -> inv.getArgument(0));

			// when
			var result = carService.save(null, "Honda", "NSX");

			// then
			assertThat(result.getManufacturer()).isEqualTo("Honda");
			assertThat(result.getName()).isEqualTo("NSX");
		}

		@Test
		void givenExistingCar_whenSave_thenUpdatesCar() {
			// given
			var id = UUID.randomUUID();
			var existing = new Car("Old Manufacturer", "Old Name");
			existing.setId(id);

			when(carRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));
			when(carRepository.saveAndFlush(any(Car.class))).thenAnswer(inv -> inv.getArgument(0));

			// when
			var result = carService.save(id, "New Manufacturer", "New Name");

			// then
			assertThat(result.getManufacturer()).isEqualTo("New Manufacturer");
			assertThat(result.getName()).isEqualTo("New Name");
		}

		@Test
		void givenDuplicateCar_whenSave_thenThrowsBusinessRuleException() {
			// given
			when(carRepository.saveAndFlush(any(Car.class)))
					.thenThrow(new DataIntegrityViolationException("unique constraint"));

			// when / then
			assertThatThrownBy(() -> carService.save(null, "Honda", "NSX"))
					.isInstanceOf(BusinessRuleException.class)
					.hasMessageContaining("already exists");
		}
	}

	@Nested
	class DeleteTest {

		@Test
		void givenCarNotUsed_whenDelete_thenRemoves() {
			// given
			var id = UUID.randomUUID();
			var car = new Car("Honda", "NSX");
			car.setId(id);
			when(carRepository.findByIdForUpdate(id)).thenReturn(Optional.of(car));
			when(raceRepository.existsByCarId(id)).thenReturn(false);
			when(seasonRepository.findAll()).thenReturn(List.of());

			// when
			carService.delete(id);

			// then
			verify(carRepository).delete(car);
		}

		@Test
		void givenCarUsedInRace_whenDelete_thenThrowsBusinessRuleException() {
			// given
			var id = UUID.randomUUID();
			var car = new Car("Honda", "NSX");
			car.setId(id);
			when(carRepository.findByIdForUpdate(id)).thenReturn(Optional.of(car));
			when(raceRepository.existsByCarId(id)).thenReturn(true);

			// when / then
			assertThatThrownBy(() -> carService.delete(id))
					.isInstanceOf(BusinessRuleException.class)
					.hasMessageContaining("race");
		}

		@Test
		void givenCarUsedInSeasonPool_whenDelete_thenThrowsBusinessRuleException() {
			// given
			var id = UUID.randomUUID();
			var car = new Car("Honda", "NSX");
			car.setId(id);
			when(carRepository.findByIdForUpdate(id)).thenReturn(Optional.of(car));
			when(raceRepository.existsByCarId(id)).thenReturn(false);

			var season = mock(Season.class);
			when(season.getCars()).thenReturn(List.of(car));
			when(seasonRepository.findAll()).thenReturn(List.of(season));

			// when / then
			assertThatThrownBy(() -> carService.delete(id))
					.isInstanceOf(BusinessRuleException.class)
					.hasMessageContaining("season pool");
		}

		@Test
		void givenCarWithImage_whenDelete_thenDeletesImageFirst() {
			// given
			var id = UUID.randomUUID();
			var car = new Car("Honda", "NSX");
			car.setId(id);
			car.setImageUrl("/uploads/cars/image.png");
			when(carRepository.findByIdForUpdate(id)).thenReturn(Optional.of(car));
			when(raceRepository.existsByCarId(id)).thenReturn(false);
			when(seasonRepository.findAll()).thenReturn(List.of());

			// when
			carService.delete(id);

			// then
			verify(fileStorageService).delete("/uploads/cars/image.png");
			verify(carRepository).delete(car);
		}
	}

	@Nested
	class UploadImageTest {

		@Test
		void givenValidImage_whenUploadImage_thenStoresAndUpdatesEntity() throws IOException {
			// given
			var id = UUID.randomUUID();
			var car = new Car("Honda", "NSX");
			car.setId(id);
			var image = mock(MultipartFile.class);

			when(carRepository.findByIdForUpdate(id)).thenReturn(Optional.of(car));
			when(fileStorageService.storeImage("cars", id, image)).thenReturn("/uploads/cars/new.png");
			when(carRepository.save(any(Car.class))).thenAnswer(inv -> inv.getArgument(0));

			// when
			carService.uploadImage(id, image);

			// then
			assertThat(car.getImageUrl()).isEqualTo("/uploads/cars/new.png");
			verify(carRepository).save(car);
		}

		@Test
		void givenExistingImage_whenUploadImage_thenDeletesOldFirst() throws IOException {
			// given
			var id = UUID.randomUUID();
			var car = new Car("Honda", "NSX");
			car.setId(id);
			car.setImageUrl("/uploads/cars/old.png");
			var image = mock(MultipartFile.class);

			when(carRepository.findByIdForUpdate(id)).thenReturn(Optional.of(car));
			when(fileStorageService.storeImage("cars", id, image)).thenReturn("/uploads/cars/new.png");
			when(carRepository.save(any(Car.class))).thenAnswer(inv -> inv.getArgument(0));

			// when
			carService.uploadImage(id, image);

			// then
			verify(fileStorageService).delete("/uploads/cars/old.png");
			assertThat(car.getImageUrl()).isEqualTo("/uploads/cars/new.png");
		}

		@Test
		void givenUploadFailure_whenUploadImage_thenThrowsBusinessRuleException() throws IOException {
			// given
			var id = UUID.randomUUID();
			var car = new Car("Honda", "NSX");
			car.setId(id);
			var image = mock(MultipartFile.class);

			when(carRepository.findByIdForUpdate(id)).thenReturn(Optional.of(car));
			when(fileStorageService.storeImage("cars", id, image))
					.thenThrow(new IOException("Disk full"));

			// when / then
			assertThatThrownBy(() -> carService.uploadImage(id, image))
					.isInstanceOf(BusinessRuleException.class)
					.hasMessageContaining("Image upload failed");
		}

		@Test
		void givenRuntimeException_whenUploadImage_thenPropagates() throws IOException {
			// given
			var id = UUID.randomUUID();
			var car = new Car("Honda", "NSX");
			car.setId(id);
			var image = mock(MultipartFile.class);

			when(carRepository.findByIdForUpdate(id)).thenReturn(Optional.of(car));
			when(fileStorageService.storeImage("cars", id, image))
					.thenThrow(new RuntimeException("unexpected error"));

			// when / then
			assertThatThrownBy(() -> carService.uploadImage(id, image))
					.isInstanceOf(RuntimeException.class)
					.isNotInstanceOf(BusinessRuleException.class);
		}
	}
}
