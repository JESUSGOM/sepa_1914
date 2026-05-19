package com.sepa1914.adminservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import org.springframework.http.HttpStatus;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /*
     * =========================================================
     * PASSWORD ENCODER
     * =========================================================
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * =========================================================
     * AUTHENTICATION MANAGER
     * =========================================================
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {

        return config.getAuthenticationManager();
    }

    /*
     * =========================================================
     * SECURITY FILTER CHAIN
     * =========================================================
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http

                /*
                 * =====================================================
                 * CSRF
                 * =====================================================
                 */
                .csrf(csrf -> csrf.disable())

                /*
                 * =====================================================
                 * AUTHORIZATION
                 * =====================================================
                 */
                .authorizeHttpRequests(auth -> auth

                        /*
                         * Recursos públicos
                         */
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/favicon.ico"
                        ).permitAll()

                        /*
                         * Login y registro web
                         */
                        .requestMatchers(
                                "/login",
                                "/registro"
                        ).permitAll()

                        /*
                         * API REST pública
                         */
                        .requestMatchers(
                                "/api/auth/**"
                        ).permitAll()

                        /*
                         * Módulos protegidos
                         */
                        .requestMatchers(
                                "/dashboard/**",
                                "/vecinos/**",
                                "/comunidades/**",
                                "/conceptos/**",
                                "/gastos/**",
                                "/presupuestos/**",
                                "/usuarios/**"
                        ).authenticated()

                        /*
                         * Todo lo demás requiere login
                         */
                        .anyRequest().authenticated()
                )

                /*
                 * =====================================================
                 * LOGIN WEB THYMELEAF
                 * =====================================================
                 */
                .formLogin(form -> form

                        .loginPage("/login")

                        .loginProcessingUrl("/login")

                        .defaultSuccessUrl("/dashboard", true)

                        .failureUrl("/login?error=true")

                        .permitAll()
                )

                /*
                 * =====================================================
                 * LOGOUT
                 * =====================================================
                 */
                .logout(logout -> logout

                        .logoutUrl("/logout")

                        .logoutSuccessUrl("/login?logout")

                        .invalidateHttpSession(true)

                        .deleteCookies("JSESSIONID")

                        .permitAll()
                )

                /*
                 * =====================================================
                 * API REST
                 * EVITA REDIRECCIÓN HTML EN APIs
                 * =====================================================
                 */
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/")
                        )
                )

                /*
                 * =====================================================
                 * HEADERS
                 * =====================================================
                 */
                .headers(headers -> headers

                        .frameOptions(frame -> frame.sameOrigin())

                        .cacheControl(cache -> cache.disable())
                );

        return http.build();
    }
}