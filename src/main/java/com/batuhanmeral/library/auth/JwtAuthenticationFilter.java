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
 * Reads the {@code Authorization: Bearer <token>} header and, for a valid token, tells Spring
 * Security who sent the request. It runs inside Spring Security's filter chain, before the filter
 * that checks the rules of SecurityConfig, so those rules see the user.
 *
 * <ul>
 *   <li>No bearer token: the request goes on without a user (anonymous). Public endpoints answer
 *       it; the others answer 401.
 *   <li>A valid token: an Authentication with the user and the role ("ROLE_MEMBER") is put into the
 *       SecurityContext for this request only. Nothing is kept between requests (stateless).
 *   <li>An invalid or expired token: 401 at once, even on a public endpoint. A client that sends a
 *       token expects to act as its user; serving it anonymously would hide the problem.
 * </ul>
 *
 * <p>Not a Spring bean on purpose. Spring Boot registers every Filter bean with the servlet
 * container, so as a bean it would also run outside Spring Security, a second time for every
 * request. SecurityConfig creates it and adds it to the security chain only.
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
        // authenticated(...) marks the token as already checked; no AuthenticationManager is
        // asked again. There are no credentials to keep (null).
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        SecurityContext context = contexts.createEmptyContext();
        context.setAuthentication(authentication);
        contexts.setContext(context);
        chain.doFilter(request, response);
    }
}
