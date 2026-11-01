package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.CarForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.CarService;
import org.ctc.domain.service.CarMetadataService.ManualMetadata;
import org.ctc.domain.model.CarCategory;
import org.ctc.domain.model.Drivetrain;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/cars")
@RequiredArgsConstructor
public class CarController {

	private final CarService carService;

	@GetMapping
	public String list(Model model) {
		model.addAttribute("cars", carService.findAllSorted());
		return "admin/cars";
	}

	@GetMapping("/new")
	public String create(Model model) {
		var form = new CarForm();
        form.setMetadataSubmitted(true);
        model.addAttribute("carForm", form);
        prepareForm(form, model);
		return "admin/car-form";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable UUID id, Model model) {
		var car = carService.findById(id);
		var form = new CarForm();
		form.setId(car.getId());
		form.setManufacturer(car.getManufacturer());
		form.setName(car.getName());
        form.setMetadataSubmitted(true);
        form.setCategory(car.getCategory());
        form.setDrivetrain(car.getDrivetrain());
        form.setModelYear(car.getModelYear());
        form.setCategoryOverride(car.isCategoryOverride());
        form.setDrivetrainOverride(car.isDrivetrainOverride());
        form.setModelYearOverride(car.isModelYearOverride());
		model.addAttribute("carForm", form);
        prepareForm(form, model);
		return "admin/car-form";
	}

	@PostMapping("/{id}/image")
	public String uploadImage(@PathVariable UUID id, @RequestParam MultipartFile image,
	                          RedirectAttributes redirectAttributes) {
		try {
			carService.uploadImage(id, image);
			redirectAttributes.addFlashAttribute("successMessage", "Image updated");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/cars/" + id + "/edit";
	}

	@PostMapping("/save")
	public String save(@Valid @ModelAttribute CarForm carForm, BindingResult result,
	                   Model model, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
            prepareForm(carForm, model);
			return "admin/car-form";
		}
		try {
			if (carForm.isMetadataSubmitted()) {
                carService.save(carForm.getId(), carForm.getManufacturer(), carForm.getName(),
                        new ManualMetadata(carForm.getCategory(), carForm.getDrivetrain(), carForm.getModelYear(),
                                carForm.isCategoryOverride(), carForm.isDrivetrainOverride(), carForm.isModelYearOverride()));
            } else {
                carService.save(carForm.getId(), carForm.getManufacturer(), carForm.getName());
            }
			redirectAttributes.addFlashAttribute("successMessage",
					"Car saved: " + carForm.getManufacturer() + " " + carForm.getName());
		} catch (BusinessRuleException e) {
            result.reject("car.save", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            prepareForm(carForm, model);
            return "admin/car-form";
        }
		return "redirect:/admin/cars";
	}

    private void prepareForm(CarForm form, Model model) {
        model.addAttribute("pageTitle", form.getId() == null ? "New Car" : "Edit Car");
        model.addAttribute("categories", CarCategory.values());
        model.addAttribute("drivetrains", Drivetrain.values());
        if (form.getId() != null) model.addAttribute("car", carService.findById(form.getId()));
    }

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
		try {
			carService.delete(id);
			redirectAttributes.addFlashAttribute("successMessage", "Car deleted");
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/cars";
	}
}
