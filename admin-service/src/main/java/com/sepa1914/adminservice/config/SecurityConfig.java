package com.sepa1914.adminservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 1. Recursos estáticos y librerías (Acceso público)
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()

                        // 2. Páginas de acceso público (Login y Registro)
                        .requestMatchers("/login", "/registro").permitAll()

                        // 3. Rutas de gestión (Requieren estar logueado)
                        // Incluye: lista, nuevo, guardar, eliminar, descargar-mandato, subir-mandato y ver-mandato-firmado
                        .requestMatchers("/vecinos/**").authenticated()
                        .requestMatchers("/comunidades/**").authenticated()
                        .requestMatchers("/conceptos/**").authenticated()

                        // 4. Cualquier otra petición (Dashboard, etc.) requiere autenticación
                        .anyRequest().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                // Deshabilitamos CSRF para facilitar la gestión de remesas y subida de archivos multipart
                .csrf(csrf -> csrf.disable())

                // Cabeceras de seguridad
                .headers(headers -> headers
                        // IMPORTANTE: Permitir que se abran los PDFs en marcos/objetos si fuera necesario
                        .frameOptions(frame -> frame.sameOrigin())
                        // Opcional: Deshabilitar caché para las descargas de mandatos si fuera necesario
                        .cacheControl(cache -> cache.disable())
                );

        return http.build();
    }
}