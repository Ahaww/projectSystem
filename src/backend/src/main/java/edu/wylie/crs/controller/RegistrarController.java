package edu.wylie.crs.controller;

import edu.wylie.crs.domain.Role;
import edu.wylie.crs.dto.OfferingVO;
import edu.wylie.crs.dto.RegistrarDtos.CloseResultVO;
import edu.wylie.crs.dto.RegistrarDtos.ProfessorUpsertRequest;
import edu.wylie.crs.dto.RegistrarDtos.ProfessorVO;
import edu.wylie.crs.dto.RegistrarDtos.StudentUpsertRequest;
import edu.wylie.crs.dto.RegistrarDtos.StudentVO;
import edu.wylie.crs.security.RoleGuard;
import edu.wylie.crs.service.OfferingService;
import edu.wylie.crs.service.RegistrarService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/registrar")
public class RegistrarController {

    private final RegistrarService registrarService;
    private final OfferingService offeringService;

    public RegistrarController(RegistrarService registrarService, OfferingService offeringService) {
        this.registrarService = registrarService;
        this.offeringService = offeringService;
    }

    @GetMapping("/offerings")
    public List<OfferingVO> offerings() {
        RoleGuard.require(Role.REGISTRAR);
        return offeringService.listForRegistrar();
    }

    @GetMapping("/students")
    public List<StudentVO> students() {
        RoleGuard.require(Role.REGISTRAR);
        return registrarService.listStudents();
    }

    @PostMapping("/students")
    public StudentVO addStudent(@Valid @RequestBody StudentUpsertRequest request) {
        RoleGuard.require(Role.REGISTRAR);
        return registrarService.addStudent(request);
    }

    @PutMapping("/students/{id}")
    public void updateStudent(@PathVariable String id, @Valid @RequestBody StudentUpsertRequest request) {
        RoleGuard.require(Role.REGISTRAR);
        registrarService.updateStudent(id, request);
    }

    @DeleteMapping("/students/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStudent(@PathVariable String id) {
        RoleGuard.require(Role.REGISTRAR);
        registrarService.deleteStudent(id);
    }

    @GetMapping("/professors")
    public List<ProfessorVO> professors() {
        RoleGuard.require(Role.REGISTRAR);
        return registrarService.listProfessors();
    }

    @PostMapping("/professors")
    public ProfessorVO addProfessor(@Valid @RequestBody ProfessorUpsertRequest request) {
        RoleGuard.require(Role.REGISTRAR);
        return registrarService.addProfessor(request);
    }

    @PutMapping("/professors/{id}")
    public void updateProfessor(@PathVariable String id, @Valid @RequestBody ProfessorUpsertRequest request) {
        RoleGuard.require(Role.REGISTRAR);
        registrarService.updateProfessor(id, request);
    }

    @DeleteMapping("/professors/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfessor(@PathVariable String id) {
        RoleGuard.require(Role.REGISTRAR);
        registrarService.deleteProfessor(id);
    }

    @PostMapping("/close-registration")
    public CloseResultVO closeRegistration() {
        RoleGuard.require(Role.REGISTRAR);
        return registrarService.closeRegistration();
    }
}
