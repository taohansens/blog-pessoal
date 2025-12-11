package br.com.taohansen.blog.config;

import br.com.taohansen.blog.security.AdminAuthorizationFilter;
import br.com.taohansen.blog.security.JwtAuthenticationFilter;
import br.com.taohansen.blog.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;

/**
 * Configuração de segurança para autenticação OAuth2 com GitHub.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final AdminAuthorizationFilter adminAuthorizationFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2SuccessHandler oauth2SuccessHandler;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable) // Desabilitar CSRF para API REST
            .authorizeExchange(exchanges -> exchanges
                // Liberar preflight CORS
                .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // Permitir acesso público a operações de leitura (GET)
                .pathMatchers("/api/posts/**").permitAll()
                // Permitir acesso aos endpoints de autenticação (frontend precisa verificar status)
                .pathMatchers("/api/auth/**").permitAll()
                // Permitir acesso ao endpoint de login do OAuth2
                .pathMatchers("/login/oauth2/**", "/oauth2/**").permitAll()
                // Todos os outros endpoints requerem autenticação
                // O AdminAuthorizationFilter fará a verificação específica para POST/PUT/DELETE
                .anyExchange().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .authenticationSuccessHandler(oauth2SuccessHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterAfter(adminAuthorizationFilter, SecurityWebFiltersOrder.AUTHORIZATION)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((exchange, ex) -> {
                    log.warn("Acesso não autorizado: {}", exchange.getRequest().getPath());
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                })
            );

        return http.build();
    }

    @Bean
    public ServerSecurityContextRepository securityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }
}

