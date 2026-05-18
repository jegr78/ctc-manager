package org.ctc.domain.service;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Car;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarService {

	private final CarRepository carRepository;
	private final RaceRepository raceRepository;
	private final SeasonRepository seasonRepository;
	private final FileStorageService fileStorageService;

	@Transactional(readOnly = true)
	public List<Car> findAllSorted() {
		return carRepository.findAllByOrderByManufacturerAscNameAsc();
	}

	@Transactional(readOnly = true)
	public Car findById(UUID id) {
		return carRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Car", id));
	}

	@Transactional
	public Car save(UUID id, String manufacturer, String name) {
		Car car;
		if (id != null) {
			car = carRepository.findById(id)
					.orElseThrow(() -> new EntityNotFoundException("Car", id));
			car.setManufacturer(manufacturer);
			car.setName(name);
		} else {
			car = new Car(manufacturer, name);
		}
		try {
			car = carRepository.saveAndFlush(car);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessRuleException("A car with this manufacturer and name already exists");
		}
		log.info("Saved car: {} {}", car.getManufacturer(), car.getName());
		return car;
	}

	@Transactional
	public void delete(UUID id) {
		var car = carRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Car", id));

		if (raceRepository.existsByCarId(id)) {
			throw new BusinessRuleException("Cannot delete: car is used in a race");
		}

		boolean usedInPool = seasonRepository.findAll().stream()
				.anyMatch(s -> s.getCars().contains(car));
		if (usedInPool) {
			throw new BusinessRuleException("Cannot delete: car is in a season pool");
		}

		if (car.getImageUrl() != null) {
			fileStorageService.delete(car.getImageUrl());
		}

		carRepository.delete(car);
		log.info("Deleted car: {} {}", car.getManufacturer(), car.getName());
	}

	@Transactional
	public void uploadImage(UUID id, MultipartFile image) {
		var car = carRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Car", id));
		try {
			if (car.getImageUrl() != null) {
				fileStorageService.delete(car.getImageUrl());
			}
			String url = fileStorageService.storeImage("cars", id, image);
			car.setImageUrl(url);
			carRepository.save(car);
			log.info("Updated image for car: {} {}", car.getManufacturer(), car.getName());
		} catch (IOException e) {
			throw new BusinessRuleException("Image upload failed: " + e.getMessage());
		}
	}
}
