package com.minimercado;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Exige login em todas as páginas e restringe /admin ao tipo ADM. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry r) {
        r.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest q, HttpServletResponse p, Object h) throws Exception {
                Usuario u = (Usuario) q.getSession().getAttribute("usuario");
                boolean adm = u != null && "ADM".equals(u.tipo);
                if (u == null || (q.getRequestURI().startsWith("/admin") && !adm)) { p.sendRedirect("/login"); return false; }
                return true;
            }
        }).excludePathPatterns("/login", "/cadastro", "/style.css", "/error", "/favicon.ico");
    }
}
