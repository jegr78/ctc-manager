package org.ctc.admin.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.CarForm;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.service.CarService;
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
		model.addAttribute("carForm", new CarForm());
		return "admin/car-form";
	}

	@GetMapping("/{id}/edit")
	public String edit(@PathVariable UUID id, Model model) {
		var car = carService.findById(id);
		var form = new CarForm();
		form.setId(car.getId());
		form.setManufacturer(car.getManufacturer());
		form.setName(car.getName());
		model.addAttribute("carForm", form);
		model.addAttribute("car", car);
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
	                   RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			return "admin/car-form";
		}
		try {
			carService.save(carForm.getId(), carForm.getManufacturer(), carForm.getName());
			redirectAttributes.addFlashAttribute("successMessage",
					"Car saved: " + carForm.getManufacturer() + " " + carForm.getName());
		} catch (BusinessRuleException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/cars";
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
