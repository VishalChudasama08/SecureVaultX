package com.securevaultx.backend.config;

import com.securevaultx.backend.security.RestSecurityHandlers;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Session-based authentication managed entirely by Spring Security:
 * <ul>
 *   <li>Login is Spring's own form-login filter on POST /api/auth/login (fields: email, password). It rotates
 *       the session id on success (session-fixation protection) and stores the SecurityContext in the session.</li>
 *   <li>CSRF protection stays ON. The session cookie is sent automatically by browsers, so state-changing
 *       requests must carry the token from GET /api/auth/csrf in the X-CSRF-TOKEN header.</li>
 *   <li>Logout (POST /api/auth/logout) invalidates the session and the CSRF token.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            // Plain (non-XOR) handler: the token returned by /api/auth/csrf is used verbatim as the header value.
            .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/api/auth/login")
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(RestSecurityHandlers.LOGIN_SUCCESS)
                .failureHandler(RestSecurityHandlers.LOGIN_FAILURE)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                .deleteCookies("JSESSIONID"))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(RestSecurityHandlers.ENTRY_POINT)
                .accessDeniedHandler(RestSecurityHandlers.ACCESS_DENIED));
        return http.build();
    }

    /** BCrypt via Spring Security; the salt and cost are embedded in each hash. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Exact-origin CORS, credentials allowed. Empty configuration = no cross-origin access at all. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = props.allowedOrigins().stream()
                .map(String::strip).filter(s -> !s.isEmpty() && !s.equals("*")).toList();
        if (!origins.isEmpty()) {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(origins);
            cfg.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
            cfg.setAllowedHeaders(List.of("Content-Type", "Accept", "X-CSRF-TOKEN", "X-Filename"));
            cfg.setExposedHeaders(List.of("Content-Disposition", "Content-Length"));
            cfg.setAllowCredentials(true);
            cfg.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", cfg);
        }
        return source;
    }
}
