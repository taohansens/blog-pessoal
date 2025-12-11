package br.com.taohansen.blog.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Filtro de autorização que verifica se o usuário autenticado é administrador
 * antes de permitir operações de escrita (POST, PUT, DELETE).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuthorizationFilter implements WebFilter {

    private final AdminService adminService;
    private final JwtService jwtService;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        // Permitir operações de leitura (GET) sem autenticação
        if (method == HttpMethod.GET && path.startsWith("/api/posts")) {
            return chain.filter(exchange);
        }

        // Ignorar preflight (já permitido em SecurityConfig)
        if (method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // Verificar se é uma operação de escrita que requer admin
        boolean isPostWrite = isWriteOperation(method) && path.startsWith("/api/posts");
        boolean isMedia = path.startsWith("/api/admin/media");
        if (isPostWrite || isMedia) {
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .flatMap(authentication -> {
                        if (!authentication.isAuthenticated()) {
                            log.warn("Tentativa de acesso não autenticado: {} {}", method, path);
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }

                        boolean isAdmin = false;
                        String userIdentifier = null;

                        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                            // Autenticação OAuth2 (sessão)
                            OAuth2User oauth2User = oauth2Token.getPrincipal();
                            if (oauth2User != null) {
                                userIdentifier = getEmailFromOAuth2User(oauth2User);
                                isAdmin = adminService.isAdmin(userIdentifier);
                            }
                        } else if (authentication instanceof UsernamePasswordAuthenticationToken) {
                            // Autenticação JWT
                            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                                String token = authHeader.substring(BEARER_PREFIX.length());
                                if (jwtService.validateToken(token)) {
                                    userIdentifier = jwtService.extractEmail(token);
                                    if (userIdentifier == null) {
                                        userIdentifier = jwtService.extractLogin(token);
                                    }
                                    isAdmin = Boolean.TRUE.equals(jwtService.extractIsAdmin(token));
                                }
                            }
                        }

                        if (!isAdmin) {
                            log.warn("Tentativa de acesso não autorizado: {} {} por usuário: {}", 
                                    method, path, userIdentifier);
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }

                        log.debug("Acesso autorizado para administrador: {} {} (usuário: {})", 
                                method, path, userIdentifier);

                        return chain.filter(exchange);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("Sem contexto de segurança: {} {}", method, path);
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }));
        }

        // Para operações de leitura ou outras rotas, continuar normalmente
        return chain.filter(exchange);
    }

    /**
     * Verifica se é uma operação de escrita (POST, PUT, DELETE).
     */
    private boolean isWriteOperation(HttpMethod method) {
        return method == HttpMethod.POST 
                || method == HttpMethod.PUT 
                || method == HttpMethod.DELETE;
    }

    /**
     * Extrai o email do usuário OAuth2.
     */
    private String getEmailFromOAuth2User(OAuth2User oauth2User) {
        Map<String, Object> attributes = oauth2User.getAttributes();
        
        // GitHub retorna o email em "email" ou em "login" (username)
        String email = (String) attributes.get("email");
        if (email == null || email.isBlank()) {
            // Se não tiver email público, usar o login
            email = (String) attributes.get("login");
        }
        
        return email != null ? email : "";
    }
}

