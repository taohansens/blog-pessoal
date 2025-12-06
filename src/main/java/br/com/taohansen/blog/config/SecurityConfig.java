package br.com.taohansen.blog.config;

import br.com.taohansen.blog.security.AdminAuthorizationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;

/**
 * Configuração de segurança para autenticação OAuth2 com GitHub.
 * 
 * Endpoints públicos (GET):
 * - GET /api/posts/** - Leitura de posts
 * 
 * Endpoints protegidos (requerem autenticação como administrador):
 * - POST /api/posts - Criar post
 * - PUT /api/posts/{id} - Atualizar post
 * - DELETE /api/posts/{id} - Deletar post
 * 
 * A verificação de administrador é feita pelo AdminAuthorizationFilter.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final AdminAuthorizationFilter adminAuthorizationFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable()) // Desabilitar CSRF para API REST
            .authorizeExchange(exchanges -> exchanges
                // Permitir acesso público a operações de leitura (GET)
                .pathMatchers("/api/posts/**").permitAll()
                // Permitir acesso ao endpoint de login do OAuth2
                .pathMatchers("/login/oauth2/**", "/oauth2/**").permitAll()
                // Todos os outros endpoints requerem autenticação
                // O AdminAuthorizationFilter fará a verificação específica para POST/PUT/DELETE
                .anyExchange().authenticated()
            )
            .oauth2Login(Customizer.withDefaults())
            .addFilterAfter(adminAuthorizationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
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

