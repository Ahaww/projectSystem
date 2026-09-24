package edu.wylie.crs.controller;

import edu.wylie.crs.domain.Role;
import edu.wylie.crs.dto.GradeDtos.ReportCardVO;
import edu.wylie.crs.security.RoleGuard;
import edu.wylie.crs.service.GradeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-card")
public class ReportCardController {

    private final GradeService gradeService;

    public ReportCardController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping("/me")
    public ReportCardVO me() {
        RoleGuard.require(Role.STUDENT);
        return gradeService.myReportCard();
    }
}
