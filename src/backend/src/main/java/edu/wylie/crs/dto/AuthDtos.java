package edu.wylie.crs.dto;

import edu.wylie.crs.domain.Role;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record UserVO(String id, String name, Role role) {
    }

    public record LoginResponse(String token, UserVO user) {
    }
}
