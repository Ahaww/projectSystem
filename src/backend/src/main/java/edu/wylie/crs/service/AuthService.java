package edu.wylie.crs.service;

import edu.wylie.crs.dto.AuthDtos.LoginRequest;
import edu.wylie.crs.dto.AuthDtos.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    void logout();
}
