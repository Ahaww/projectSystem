package edu.wylie.crs.security;

public final class AuthHolder {

    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

    private AuthHolder() {
    }

    public static void set(AuthUser user) {
        CURRENT.set(user);
    }

    public static AuthUser get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
