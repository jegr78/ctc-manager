package org.ctc.admin.controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.gt7sync.Gt7CarShotBulkService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Car list actions for loading GT7 car shots of every car in the background; actions return to the car list. */
@Controller
@RequiredArgsConstructor
public class Gt7CarShotBulkController {
    static final String CAR_LIST = "redirect:/admin/cars#gt7-car-images";
    private final Gt7CarShotBulkService bulk;

    @PostMapping("/admin/cars/gt7-images")
    public String start(RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("successMessage", "Started: " + bulk.start().message());
        } catch (BusinessRuleException rejected) {
            redirect.addFlashAttribute("errorMessage", rejected.getMessage());
        }
        return CAR_LIST;
    }

    @PostMapping("/admin/cars/gt7-images/stop")
    public String stop(RedirectAttributes redirect) {
        try {
            bulk.stop();
            redirect.addFlashAttribute("successMessage", "The GT7 image run stops after the current car.");
        } catch (BusinessRuleException rejected) {
            redirect.addFlashAttribute("errorMessage", rejected.getMessage());
        }
        return CAR_LIST;
    }

    @GetMapping("/admin/cars/gt7-images/status")
    @ResponseBody
    public Map<String, Object> status() {
        var status = bulk.status();
        return status == null ? Map.of("running", false, "message", "") : Map.of("running", status.running(), "message", status.message());
    }
}
