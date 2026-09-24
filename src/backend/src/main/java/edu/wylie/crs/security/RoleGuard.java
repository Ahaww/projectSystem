package edu.wylie.crs.security;

import edu.wylie.crs.common.ApiException;
import edu.wylie.crs.domain.Role;
import org.springframework.http.HttpStatus;

public final class RoleGuard {

    private RoleGuard() {
    }

    public static AuthUser require(Role... allowed) {
        AuthUser user = AuthHolder.get();
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        for (Role role : allowed) {
            if (user.role() == role) {
                return user;
            }
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "当前角色无权访问该接口");
    }
}
