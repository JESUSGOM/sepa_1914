package com.sepa1914.adminservice.config;

import jakarta.servlet.*;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtro GTI para inyectar el nombre del usuario logueado en los hilos de log.
 */
@Component
public class UserLoggingFilter implements Filter {

    private static final String USER_KEY = "user";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 1. Buscamos quién está al volante en Spring Security
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            // 2. Pegamos el nombre al contexto del log (MDC)
            MDC.put(USER_KEY, auth.getName());
        } else {
            // 3. Si es una tarea interna o no hay login, marcamos como SISTEMA
            MDC.put(USER_KEY, "SISTEMA");
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // 4. Limpieza obligatoria: Evita que el nombre "manche" otras peticiones
            MDC.remove(USER_KEY);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void destroy() {}
}