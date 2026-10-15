package com.batuhanmeral.library.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <token>} header'ını okur ve geçerli token'ın kullanıcısını
 * SecurityContext'e koyar.
 *
 * <p>Token yoksa istek anonim devam eder; geçersiz token herkese açık uçta bile 401 alır. Bilerek
 * bean değil: Filter bean'leri servlet container'a da kaydedilir ve iki kez çalışırdı.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    private final AuthenticationEntryPoint entryPoint;

    private final SecurityContextHolderStrategy contexts =
            SecurityContextHolder.getContextHolderStrategy();

    public JwtAuthenticationFilter(JwtService jwtService, AuthenticationEntryPoint entryPoint) {
        this.jwtService = jwtService;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            chain.doFilter(request, response);
            return;
        }
        AuthenticatedUser user;
        try {
            user = jwtService.parse(header.substring(BEARER.length()).strip());
        } catch (InvalidTokenException e) {
            contexts.clearContext();
            entryPoint.commence(request, response, e);
            return;
        }
        // authenticated(...) token'ı kontrol edilmiş sayar; saklanacak credential yok (null).
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        SecurityContext context = contexts.createEmptyContext();
        context.setAuthentication(authentication);
        contexts.setContext(context);
        chain.doFilter(request, response);
    }
}
