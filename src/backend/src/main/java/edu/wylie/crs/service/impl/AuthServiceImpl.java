package edu.wylie.crs.service.impl;

import edu.wylie.crs.common.ApiException;
import edu.wylie.crs.dto.AuthDtos.LoginRequest;
import edu.wylie.crs.dto.AuthDtos.LoginResponse;
import edu.wylie.crs.dto.AuthDtos.UserVO;
import edu.wylie.crs.entity.UserAccount;
import edu.wylie.crs.repository.UserAccountRepository;
import edu.wylie.crs.security.AuthUser;
import edu.wylie.crs.security.JwtService;
import edu.wylie.crs.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String lookup = request.username() == null ? "" : request.username().trim();
        UserAccount account = userAccountRepository.findByUsername(lookup).orElse(null);
        if (account == null || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        AuthUser authUser = new AuthUser(account.getId(), account.getUsername(), account.getName(), account.getRole());
        String token = jwtService.issue(authUser);
        return new LoginResponse(token, new UserVO(account.getId(), account.getName(), account.getRole()));
    }

    @Override
    public void logout() {
        // 契约允许空实现：令牌在客户端丢弃即可，不做服务端黑名单
    }
}
