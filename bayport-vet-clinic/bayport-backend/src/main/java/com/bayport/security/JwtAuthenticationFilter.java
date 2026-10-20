package com.bayport.security;

import com.bayport.auth.JwtService;
import com.bayport.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final AuditLogService auditLogService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService,
                                   AuditLogService auditLogService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.auditLogService = auditLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7).trim();
        if (jwt.isEmpty()) {
            reject(request, response, "blank", "Rejected token");
            return;
        }

        try {
            String username = jwtService.extractUsername(jwt);
            String scope = jwtService.extractScope(jwt);
            if (username == null || username.isBlank()) {
                reject(request, response, "unknown", "Rejected token: missing subject");
                return;
            }
            if (jwtService.isTokenExpired(jwt)) {
                reject(request, response, username, "Rejected token: expired");
                return;
            }
            if (!TokenScopes.allowed(scope, request.getMethod(), request.getRequestURI())) {
                reject(request, response, username, "Rejected token: scope not allowed for this request");
                return;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (!userDetails.isEnabled()) {
                reject(request, response, username, "Rejected token: account disabled");
                return;
            }

            if (TokenScopes.MFA.equals(scope)) {
                if (!jwtService.isScopedTokenValid(jwt, TokenScopes.MFA)) {
                    reject(request, response, username, "Rejected token: MFA token invalid");
                    return;
                }
                filterChain.doFilter(request, response);
                return;
            }

            if (TokenScopes.MFA_SETUP.equals(scope)) {
                if (!jwtService.isScopedTokenValid(jwt, TokenScopes.MFA_SETUP)) {
                    reject(request, response, username, "Rejected token: setup token invalid");
                    return;
                }
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_MFA_SETUP"))
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                filterChain.doFilter(request, response);
                return;
            }

            int version = 0;
            if (userDetails instanceof CustomUserDetails details) {
                version = details.getUser().getTokenVersion();
            }
            if (!jwtService.isAccessTokenValid(jwt, username, version)) {
                reject(request, response, username, "Rejected token: not a current access token");
                return;
            }

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName();
            auditLogService.log("TOKEN_REJECTED", "Auth", "unknown",
                    LogScrubber.scrub("Rejected token: " + reason), request);
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String entityId, String details)
            throws IOException {
        auditLogService.log("TOKEN_REJECTED", "Auth", entityId, LogScrubber.scrub(details), request);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Sign in again.\"}");
    }
}
