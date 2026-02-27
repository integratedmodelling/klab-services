package org.example.localhostneo4j.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppSecurityProperties securityProperties) throws Exception {
        Set<String> allowedHosts = Set.copyOf(securityProperties.allowedHosts());
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .addFilterBefore(new LocalhostOnlyFilter(allowedHosts), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

        return http.build();
    }

    private static final class LocalhostOnlyFilter extends OncePerRequestFilter {

        private final Set<String> allowedHosts;

        private LocalhostOnlyFilter(Set<String> allowedHosts) {
            this.allowedHosts = allowedHosts;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String remoteHost = request.getRemoteAddr();
            if (!allowedHosts.contains(remoteHost)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only localhost connections are allowed");
                return;
            }
            filterChain.doFilter(request, response);
        }
    }
}
