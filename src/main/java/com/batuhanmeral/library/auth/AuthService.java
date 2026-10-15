package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.auth.dto.LoginRequest;
import com.batuhanmeral.library.auth.dto.RegisterRequest;
import com.batuhanmeral.library.auth.dto.TokenResponse;
import com.batuhanmeral.library.common.audit.AuditAction;
import com.batuhanmeral.library.common.audit.Auditable;
import com.batuhanmeral.library.common.error.DuplicateResourceException;
import com.batuhanmeral.library.user.Role;
import com.batuhanmeral.library.user.User;
import com.batuhanmeral.library.user.UserMapper;
import com.batuhanmeral.library.user.UserRepository;
import com.batuhanmeral.library.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;

    private final JwtService jwtService;

    private final JwtProperties jwtProperties;

    /**
     * Üye oluşturur. E-posta küçük harfle saklanır; aynı anda gelen iki kayıttan ikincisini unique
     * constraint reddeder (409).
     */
    @Auditable(action = AuditAction.USER_REGISTER, target = "#result?.id()")
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A user with this email already exists.");
        }
        User user =
                new User(
                        email,
                        passwordEncoder.encode(request.password()),
                        request.fullName().strip(),
                        Role.MEMBER);
        user.setPhone(request.phone());
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Şifreyi kontrol edip token verir. Yanlış şifre de bilinmeyen e-posta da 401
     * INVALID_CREDENTIALS olur.
     */
    @Auditable(action = AuditAction.LOGIN)
    public TokenResponse login(LoginRequest request) {
        Authentication authentication =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                                request.email(), request.password()));
        UserAccount account = (UserAccount) authentication.getPrincipal();
        AccessToken token = jwtService.issue(new AuthenticatedUser(account.id(), account.role()));
        return new TokenResponse(token.token(), TOKEN_TYPE, jwtProperties.expiration().toSeconds());
    }
}
