package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.auth.dto.LoginRequest;
import com.batuhanmeral.library.auth.dto.RegisterRequest;
import com.batuhanmeral.library.auth.dto.TokenResponse;
import com.batuhanmeral.library.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Open to everyone (SecurityConfig): these are the endpoints that give a client its token. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 201 without a Location header: there is no GET /users/{id}. The new member logs in next and
     * reads the profile at GET /users/me.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** POST, not GET: the password must not end up in a URL, which servers and proxies log. */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
