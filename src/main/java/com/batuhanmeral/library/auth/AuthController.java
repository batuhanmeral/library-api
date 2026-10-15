package com.batuhanmeral.library.auth;

import com.batuhanmeral.library.auth.dto.LoginRequest;
import com.batuhanmeral.library.auth.dto.RegisterRequest;
import com.batuhanmeral.library.auth.dto.TokenResponse;
import com.batuhanmeral.library.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Herkese açık (SecurityConfig): istemci token'ını buradan alır. */
@Tag(name = "Auth")
@SecurityRequirements
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    /** Location header'ı yok çünkü GET /users/{id} yok; üye giriş yapıp GET /users/me'yi okur. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** GET değil POST: şifre, sunucuların log'ladığı URL'ye düşmemeli. */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
