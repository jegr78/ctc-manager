package org.ctc.admin.controller;

import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.admin.service.DataAuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/data-audit")
@RequiredArgsConstructor
public class DataAuditController {

	private final DataAuditService dataAuditService;

	@GetMapping
	public String show(Model model) {
		model.addAttribute("report", dataAuditService.audit());
		model.addAttribute("resolutions", DataAuditFinding.Resolution.values());
		return "admin/data-audit";
	}
}
