package com.lms.tracker.security;

import com.lms.tracker.util.LogSanitizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates API requests that carry "Authorization: Bearer &lt;jwt&gt;".
 *
 * A present-but-invalid token (bad signature, expired, revoked, unknown or disabled user)
 * is rejected immediately with 401. A request without a token continues anonymously and
 * is then rejected by the authorization rules if the endpoint needs authentication.
 *
 * Deliberately NOT a Spring bean, so Spring Boot does not also register it as a global servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_TOKEN_LENGTH = 4096;

    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;
    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TokenRevocationService tokenRevocationService,
                                   CustomUserDetailsService userDetailsService,
                                   AuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtService = jwtService;
        this.tokenRevocationService = tokenRevocationService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // requestURI minus context path works both in the servlet container and in MockMvc
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            if (token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
                throw new InvalidTokenException("Invalid or expired token");
            }
            TokenClaims claims = jwtService.parse(token);
            if (tokenRevocationService.isRevoked(claims.tokenId())) {
                throw new InvalidTokenException("Token has been revoked. Please log in again");
            }
            AuthenticatedUser user = userDetailsService.loadUserById(claims.userId());
            if (!user.isEnabled()) {
                throw new InvalidTokenException("Account is disabled");
            }
            user.eraseCredentials();

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
            authentication.setDetails(new TokenDetails(claims.tokenId(), claims.expiresAt()));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            log.warn("SECURITY Rejected bearer token: reason='{}' {} {}", ex.getMessage(),
                    request.getMethod(), LogSanitizer.clean(request.getRequestURI()));
            authenticationEntryPoint.commence(request, response, ex);
            return;
        }

        chain.doFilter(request, response);
    }
}
