package edu.wylie.crs;

/** 演示账号：用户名=系统编号，密码与编号相同。测试里仍可用 student/teacher/admin 别名。 */
final class TestAuth {

    private TestAuth() {
    }

    static String account(String username) {
        if (username == null) {
            return "";
        }
        return switch (username.trim().toLowerCase()) {
            case "student", "j.davis", "s001" -> "S001";
            case "teacher", "morgan", "p001" -> "P001";
            case "admin", "wang", "r001" -> "R001";
            case "s002" -> "S002";
            default -> username.trim();
        };
    }

    static String json(String username) {
        String id = account(username);
        return "{\"username\":\"" + id + "\",\"password\":\"" + id + "\"}";
    }
}
