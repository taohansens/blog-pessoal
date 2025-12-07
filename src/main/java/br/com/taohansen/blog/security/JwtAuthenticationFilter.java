package br.com.taohansen.blog.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * Filtro que valida tokens JWT nas requisições.
 * Se um token JWT válido for encontrado, cria uma autenticação baseada no token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtService jwtService;
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            // Sem token JWT, continuar normalmente (pode ter autenticação OAuth2)
            return chain.filter(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        
        log.debug("Token JWT recebido, validando...");
        
        if (jwtService.validateToken(token)) {
            try {
                String email = jwtService.extractEmail(token);
                String login = jwtService.extractLogin(token);
                Boolean isAdmin = jwtService.extractIsAdmin(token);
                
                log.debug("Token JWT válido - Email: {}, Login: {}, Admin: {}", email, login, isAdmin);
                
                // Criar autenticação baseada no JWT
                List<SimpleGrantedAuthority> authorities = Collections.emptyList();
                if (Boolean.TRUE.equals(isAdmin)) {
                    authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
                }
                
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                    email != null ? email : login,
                    token, // Usar o token como credentials para referência
                    authorities
                );
                
                SecurityContext securityContext = new SecurityContextImpl(authentication);
                
                log.info("Autenticação JWT criada para: {} (admin: {})", 
                        email != null ? email : login, isAdmin);
                
                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
            } catch (Exception e) {
                log.error("Erro ao processar token JWT: {}", e.getMessage(), e);
            }
        } else {
            log.warn("Token JWT inválido ou expirado");
        }
        
        return chain.filter(exchange);
    }
}

