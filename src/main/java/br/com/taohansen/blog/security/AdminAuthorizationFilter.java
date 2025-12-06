package br.com.taohansen.blog.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        // Permitir operações de leitura (GET) sem autenticação
        if (method == HttpMethod.GET && path.startsWith("/api/posts")) {
            return chain.filter(exchange);
        }

        // Verificar se é uma operação de escrita que requer admin
        if (isWriteOperation(method) && path.startsWith("/api/posts")) {
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .flatMap(authentication -> {
                        if (!authentication.isAuthenticated()) {
                            log.warn("Tentativa de acesso não autenticado: {} {}", method, path);
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }

                        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                            OAuth2User oauth2User = oauth2Token.getPrincipal();
                            String email = null;
                            if (oauth2User != null) {
                                email = getEmailFromOAuth2User(oauth2User);
                            }

                            if (!adminService.isAdmin(email)) {
                                log.warn("Tentativa de acesso não autorizado: {} {} por usuário: {}", 
                                        method, path, email);
                                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                return exchange.getResponse().setComplete();
                            }

                            log.debug("Acesso autorizado para administrador: {} {}", method, path);
                        } else {
                            log.warn("Autenticação não é OAuth2: {}", authentication.getClass());
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }

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

