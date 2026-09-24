package edu.wylie.crs.controller;

import edu.wylie.crs.domain.Role;
import edu.wylie.crs.dto.GradeDtos.RosterRowVO;
import edu.wylie.crs.dto.GradeDtos.SaveGradesRequest;
import edu.wylie.crs.dto.GradeDtos.TaughtOfferingVO;
import edu.wylie.crs.dto.OfferingVO;
import edu.wylie.crs.dto.ProfessorDtos.UpdateTeachingRequest;
import edu.wylie.crs.security.RoleGuard;
import edu.wylie.crs.service.GradeService;
import edu.wylie.crs.service.TeachingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/professor")
public class ProfessorController {

    private final GradeService gradeService;
    private final TeachingService teachingService;

    public ProfessorController(GradeService gradeService, TeachingService teachingService) {
        this.gradeService = gradeService;
        this.teachingService = teachingService;
    }

    @GetMapping("/offerings")
    public List<TaughtOfferingVO> offerings() {
        RoleGuard.require(Role.PROFESSOR);
        return gradeService.taughtOfferings();
    }

    @GetMapping("/offerings/{id}/roster")
    public List<RosterRowVO> roster(@PathVariable String id) {
        RoleGuard.require(Role.PROFESSOR);
        return gradeService.roster(id);
    }

    @PutMapping("/offerings/{id}/grades")
    public void saveGrades(@PathVariable String id, @RequestBody SaveGradesRequest request) {
        RoleGuard.require(Role.PROFESSOR);
        gradeService.saveGrades(id, request);
    }

    @GetMapping("/eligible")
    public List<OfferingVO> eligible() {
        RoleGuard.require(Role.PROFESSOR);
        return teachingService.eligible();
    }

    @PutMapping("/teaching")
    public void updateTeaching(@RequestBody UpdateTeachingRequest request) {
        RoleGuard.require(Role.PROFESSOR);
        teachingService.updateTeaching(request.offeringIds());
    }
}
