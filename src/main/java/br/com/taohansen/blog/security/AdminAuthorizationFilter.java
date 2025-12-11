package br.com.taohansen.blog.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filtro reativo responsável por garantir que apenas administradores executem
 * operações sensíveis nas APIs protegidas.
 * <p>
 * Permite livremente leituras públicas de posts, mas para operações de escrita
 * (POST, PUT, DELETE) em `/api/posts` e qualquer acesso a `/api/admin/media`,
 * delega ao {@link AdminAuthorizationService} a decisão de autenticação/autorização,
 * retornando códigos HTTP adequados (401/403) quando necessário.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuthorizationFilter implements WebFilter {

    private final AdminAuthorizationService adminAuthorizationService;

    /**
     * Aplica a verificação de autorização em cada requisição, permitindo ou negando
     * o prosseguimento da cadeia de filtros.
     *
     * @param exchange contexto da requisição/resposta
     * @param chain    cadeia de filtros WebFlux
     * @return {@link Mono} que completa quando a requisição é processada ou encerrada
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (method == HttpMethod.GET && path.startsWith("/api/posts")) {
            return chain.filter(exchange);
        }

        if (method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        boolean isPostWrite = isWriteOperation(method) && path.startsWith("/api/posts");
        boolean isMedia = path.startsWith("/api/admin/media");
        if (isPostWrite || isMedia) {
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .flatMap(authentication -> {
                        AdminAuthorizationService.AdminAuthResult result =
                                adminAuthorizationService.evaluate(exchange, authentication);

                        if (!result.authenticated()) {
                            log.warn("Tentativa de acesso não autenticado: {} {}", method, path);
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        }

                        if (!result.admin()) {
                            log.warn("Tentativa de acesso não autorizado: {} {} por usuário: {}",
                                    method, path, result.userIdentifier());
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }

                        log.debug("Acesso autorizado para administrador: {} {} (usuário: {})",
                                method, path, result.userIdentifier());

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

    private boolean isWriteOperation(HttpMethod method) {
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.DELETE;
    }
}

