package edu.wylie.crs.controller;

import edu.wylie.crs.dto.AuthDtos.LoginRequest;
import edu.wylie.crs.dto.AuthDtos.LoginResponse;
import edu.wylie.crs.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logout();
    }
}
