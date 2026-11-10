package com.store.procesos.config;

import com.store.procesos.repository.AdministradorRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  public static final String ADMIN_HEADER = "X-Admin-Request";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService users(AdministradorRepository repository) {
    return username ->
        repository
            .findByUsuario(username)
            .map(
                a ->
                    User.withUsername(a.getUsuario())
                        .password(a.getPasswordHash())
                        .roles("ADMIN")
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("Credenciales invalidas"));
  }

  @Bean
  SecurityFilterChain chain(
      HttpSecurity http,
      @Value("${app.docs-public}") boolean docsPublic,
      CorsConfigurationSource cors)
      throws Exception {
    http.cors(c -> c.configurationSource(cors))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(c -> c.disable())
        .formLogin(c -> c.disable())
        .logout(c -> c.disable())
        .requestCache(c -> c.disable())
        .authorizeHttpRequests(
            a -> {
              a.requestMatchers(HttpMethod.GET, "/api/catalogo/**", "/api/media/**", "/api/health").permitAll();
              if (docsPublic)
                a.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll();
              else
                a.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .hasRole("ADMIN");
              a.requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().denyAll();
            })
        .httpBasic(
            b ->
                b.authenticationEntryPoint(
                    (req, res, e) -> securityError(res, 401, "Autenticacion requerida")))
        .exceptionHandling(
            e ->
                e.accessDeniedHandler((req, res, ex) -> securityError(res, 403, "Acceso denegado")))
        .addFilterAfter(
            new OncePerRequestFilter() {
              @Override
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                // Header no simple: impide escrituras CSRF con credenciales Basic cacheadas por el
                // navegador.
                if (req.getRequestURI()
                        .substring(req.getContextPath().length())
                        .startsWith("/api/admin/")
                    && org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .getAuthentication()
                        != null
                    && !Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())
                    && !"true".equals(req.getHeader(ADMIN_HEADER))) {
                  securityError(res, 403, "Se requiere X-Admin-Request: true");
                  return;
                }
                chain.doFilter(req, res);
              }
            },
            BasicAuthenticationFilter.class);
    return http.build();
  }

  private static void securityError(HttpServletResponse res, int status, String detail)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/problem+json");
    res.setCharacterEncoding("UTF-8");
    if (status == 401) res.setHeader("WWW-Authenticate", "Basic realm=\"store\"");
    res.getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\""
                + (status == 401 ? "Unauthorized" : "Forbidden")
                + "\",\"status\":"
                + status
                + ",\"detail\":\""
                + detail
                + "\"}");
  }

  @Bean
  CorsConfigurationSource cors(@Value("${app.cors-origins}") String origins) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(
        Arrays.stream(origins.split(",")).map(String::strip).filter(x -> !x.isEmpty()).toList());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Last-Event-ID", ADMIN_HEADER));
    config.setExposedHeaders(List.of("Location", "ETag"));
    config.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
