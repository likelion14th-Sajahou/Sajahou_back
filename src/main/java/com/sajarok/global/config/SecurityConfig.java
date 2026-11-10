package com.sajarok.global.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(Customizer.withDefaults())
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/google",
                        "/api/v1/auth/signup",
                        "/api/v1/auth/demo")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                          response.setContentType("application/json");
                          response.setCharacterEncoding("UTF-8");
                          response
                              .getWriter()
                              .write(
                                  """
                                                        {"success":false,"data":null,"error":{"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}}
                                                        """);
                        })
                    .accessDeniedHandler(
                        (request, response, exception) -> {
                          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                          response.setContentType("application/json");
                          response.setCharacterEncoding("UTF-8");

                          if (exception instanceof CsrfException) {
                            response
                                .getWriter()
                                .write(
                                    """
                                                            {"success":false,"data":null,"error":{"code":"CSRF_INVALID","message":"보안 토큰이 유효하지 않습니다. 다시 시도해 주세요."}}
                                                            """);
                            return;
                          }

                          response
                              .getWriter()
                              .write(
                                  """
                                                        {"success":false,"data":null,"error":{"code":"FORBIDDEN","message":"이 요청을 처리할 권한이 없습니다."}}
                                                        """);
                        }))
        .build();
  }
}
