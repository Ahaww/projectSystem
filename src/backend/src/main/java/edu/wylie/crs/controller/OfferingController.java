package edu.wylie.crs.controller;

import edu.wylie.crs.domain.Role;
import edu.wylie.crs.dto.OfferingVO;
import edu.wylie.crs.security.RoleGuard;
import edu.wylie.crs.service.OfferingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OfferingController {

    private final OfferingService offeringService;

    public OfferingController(OfferingService offeringService) {
        this.offeringService = offeringService;
    }

    @GetMapping("/offerings")
    public List<OfferingVO> offerings(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dept
    ) {
        RoleGuard.require(Role.STUDENT, Role.PROFESSOR, Role.REGISTRAR);
        return offeringService.listForStudent(keyword, dept);
    }
}
