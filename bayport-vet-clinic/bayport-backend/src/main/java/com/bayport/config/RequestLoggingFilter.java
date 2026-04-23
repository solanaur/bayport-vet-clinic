package com.bayport.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long time = System.currentTimeMillis() - start;
            int status = response.getStatus();

            log.info("{} {}{} -> {} ({} ms)",
                    method,
                    uri,
                    (query != null ? "?" + query : ""),
                    status,
                    time
            );
        }
    }
}
