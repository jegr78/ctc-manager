package de.ctc.admin.controller;

import de.ctc.admin.dto.CarForm;
import de.ctc.domain.model.Car;
import de.ctc.domain.repository.CarRepository;
import de.ctc.domain.repository.RaceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/admin/cars")
@RequiredArgsConstructor
public class CarController {

    private final CarRepository carRepository;
    private final RaceRepository raceRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("cars", carRepository.findAllByOrderByManufacturerAscNameAsc());
        return "admin/cars";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("carForm", new CarForm());
        return "admin/car-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable UUID id, Model model) {
        var car = carRepository.findById(id).orElseThrow();
        var form = new CarForm();
        form.setId(car.getId());
        form.setManufacturer(car.getManufacturer());
        form.setName(car.getName());
        model.addAttribute("carForm", form);
        return "admin/car-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute CarForm carForm, BindingResult result,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "admin/car-form";
        }
        if (carForm.getId() != null) {
            var existing = carRepository.findById(carForm.getId()).orElseThrow();
            existing.setManufacturer(carForm.getManufacturer());
            existing.setName(carForm.getName());
            carRepository.save(existing);
        } else {
            carRepository.save(new Car(carForm.getManufacturer(), carForm.getName()));
        }
        log.info("Saved car: {} {}", carForm.getManufacturer(), carForm.getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Car saved: " + carForm.getManufacturer() + " " + carForm.getName());
        return "redirect:/admin/cars";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        var car = carRepository.findById(id).orElseThrow();
        if (raceRepository.existsByCarId(id)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete car: still used in a race.");
            return "redirect:/admin/cars";
        }
        carRepository.delete(car);
        log.info("Deleted car: {} {}", car.getManufacturer(), car.getName());
        redirectAttributes.addFlashAttribute("successMessage",
                "Car deleted: " + car.getManufacturer() + " " + car.getName());
        return "redirect:/admin/cars";
    }
}
