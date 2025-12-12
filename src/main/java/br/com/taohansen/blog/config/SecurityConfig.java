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
 * Configura a segurança reativa da aplicação (WebFlux).
 * <p>
 * - Desabilita CSRF para uso como API REST.
 * - Libera leitura pública de posts e endpoints de autenticação/OAuth2.
 * - Autentica via JWT (filtro) e, após autenticado, delega ao filtro de autorização
 *   para checagem de privilégios de administrador em operações sensíveis.
 * - Aplica handler de sucesso do OAuth2 para emitir token/admin quando apropriado.
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

    /**
     * Define a cadeia de filtros de segurança:
     * <ul>
     *     <li>CSRF desabilitado (API REST).</li>
     *     <li>Leituras públicas em `/api/posts/**` e endpoints de auth/OAuth2.</li>
     *     <li>Demais rotas requerem autenticação (JWT ou sessão OAuth2).</li>
     *     <li>`JwtAuthenticationFilter` antes da fase AUTHENTICATION.</li>
     *     <li>`AdminAuthorizationFilter` após AUTHORIZATION para validar privilégios de admin.</li>
     * </ul>
     *
     * @param http builder reativo do Spring Security
     * @return cadeia de filtros configurada
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/favicon.ico").permitAll()
                .pathMatchers("/api/posts/**").permitAll()
                .pathMatchers("/api/auth/**").permitAll()
                .pathMatchers("/login/oauth2/**", "/oauth2/**").permitAll()
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

    /**
     * Usa a sessão reativa como repositório de contexto de segurança
     * para armazenar Authentication entre requisições.
     *
     * @return repositório de contexto baseado em WebSession
     */
    @Bean
    public ServerSecurityContextRepository securityContextRepository() {
        return new WebSessionServerSecurityContextRepository();
    }
}

