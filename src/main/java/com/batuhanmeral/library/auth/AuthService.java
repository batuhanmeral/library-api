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
     * Creates a member. The address is stored in lower case, so "Ada@Example.com" and
     * "ada@example.com" are one account. Two registrations with the same address at the same time
     * both pass the check; the unique constraint then rejects the second (409 DATA_CONFLICT).
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
     * Checks the password and issues a token. A wrong password and an unknown address both end in
     * BadCredentialsException, which GlobalExceptionHandler answers with 401 INVALID_CREDENTIALS.
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
