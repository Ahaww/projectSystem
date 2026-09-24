package edu.wylie.crs.security;

import edu.wylie.crs.domain.Role;

public record AuthUser(String id, String username, String name, Role role) {
}
