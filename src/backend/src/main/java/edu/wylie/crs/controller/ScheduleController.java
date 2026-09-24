package edu.wylie.crs.controller;

import edu.wylie.crs.domain.Role;
import edu.wylie.crs.dto.ScheduleDtos.SaveScheduleRequest;
import edu.wylie.crs.dto.ScheduleDtos.ScheduleVO;
import edu.wylie.crs.security.RoleGuard;
import edu.wylie.crs.service.ScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/me")
    public ScheduleVO getMine() {
        RoleGuard.require(Role.STUDENT);
        return scheduleService.getMine();
    }

    @PutMapping("/me")
    public ScheduleVO save(@RequestBody SaveScheduleRequest request) {
        RoleGuard.require(Role.STUDENT);
        return scheduleService.save(request.items());
    }

    @PostMapping("/me/submit")
    public ScheduleVO submit(@RequestBody SaveScheduleRequest request) {
        RoleGuard.require(Role.STUDENT);
        return scheduleService.submit(request.items());
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine() {
        RoleGuard.require(Role.STUDENT);
        scheduleService.deleteMine();
    }
}
