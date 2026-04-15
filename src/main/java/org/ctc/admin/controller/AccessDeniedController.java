package org.ctc.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccessDeniedController {

	@GetMapping("/admin/access-denied")
	public String accessDenied(Model model) {
		model.addAttribute("status", 403);
		model.addAttribute("error", "Access Denied");
		model.addAttribute("message", "You do not have permission to access this resource.");
		return "admin/access-denied";
	}
}
