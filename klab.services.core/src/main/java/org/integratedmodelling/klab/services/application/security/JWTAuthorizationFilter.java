package org.integratedmodelling.klab.services.application.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

public class JWTAuthorizationFilter extends BasicAuthenticationFilter {

    public JWTAuthorizationFilter(AuthenticationManager authManager) {
        super(authManager);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String tokenString = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (tokenString != null) {
            try {
                EngineAuthorization token = JWTAuthenticationManager.INSTANCE.validateJwt(tokenString);
                if (token != null && token.isAuthenticated()) {
                    SecurityContextHolder.getContext().setAuthentication(token);
                }
            } catch (Throwable e) {
                logger.error("Failed to extract JWT token: ", e);
                throw e;
            }
        }
        chain.doFilter(req, res);
    }

}
