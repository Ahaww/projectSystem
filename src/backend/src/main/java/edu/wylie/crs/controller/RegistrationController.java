package edu.wylie.crs.controller;

import edu.wylie.crs.domain.Role;
import edu.wylie.crs.dto.RegistrarDtos.RegistrationStatusVO;
import edu.wylie.crs.security.RoleGuard;
import edu.wylie.crs.service.OfferingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registration")
public class RegistrationController {

    private final OfferingService offeringService;

    public RegistrationController(OfferingService offeringService) {
        this.offeringService = offeringService;
    }

    @GetMapping("/status")
    public RegistrationStatusVO status() {
        RoleGuard.require(Role.STUDENT, Role.PROFESSOR, Role.REGISTRAR);
        return offeringService.status();
    }
}
