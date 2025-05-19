package org.integratedmodelling.klab.services.application.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class ServiceSecurityConfiguration {

  @Autowired ServiceAuthorizationManager authorizationManager;

  @Bean
  public AuthenticationManager authenticationManager(
      AuthenticationConfiguration authenticationConfiguration) throws Exception {
    return authenticationConfiguration.getAuthenticationManager();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // TODO reintegrate?       http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy
    //  .NEVER);
    var authenticationManager =
        authenticationManager(http.getSharedObject(AuthenticationConfiguration.class));
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/public/**",
                        "/dt",
                        "/graphiql",
                        "/actuator/**",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/api.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new TokenAuthorizationFilter(authenticationManager, authorizationManager),
            UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
