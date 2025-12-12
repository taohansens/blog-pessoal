package br.com.taohansen.blog.controllers;

import br.com.taohansen.blog.security.AdminAuthorizationService;
import br.com.taohansen.blog.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller para endpoints de autenticação e autorização.
 * Fornece informações sobre o usuário autenticado para o frontend.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AdminAuthorizationService adminAuthorizationService;
    private final JwtService jwtService;

    /**
     * Retorna informações sobre o usuário autenticado (OAuth2 ou JWT).
     *
     * @param exchange contexto da requisição (necessário para ler header Authorization quando JWT)
     * @return informações do usuário ou 401 se não autenticado
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<Map<String, Object>>> getCurrentUser(
            ServerWebExchange exchange) {

        // Ler autenticação do contexto de segurança (funciona mesmo com permitAll)
        //noinspection DataFlowIssue
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> {
                    log.debug("GET /api/auth/me - Authentication: {}, isAuthenticated: {}",
                            authentication.getClass().getSimpleName(),
                            authentication.isAuthenticated());

                    if (!authentication.isAuthenticated()) {
                        log.warn("GET /api/auth/me - Não autenticado");
                        return Mono.just(ResponseEntity.status(401).<Map<String, Object>>build());
                    }

                    return resolveAdminUser(authentication, exchange)
                            .map(result -> {
                                Map<String, Object> userInfo = new HashMap<>();
                                userInfo.put("authenticated", true);
                                userInfo.put("email", result.email());
                                userInfo.put("login", result.login());
                                userInfo.put("name", result.name());
                                userInfo.put("avatarUrl", result.avatarUrl());
                                userInfo.put("isAdmin", true);
                                userInfo.put("token", result.token());
                                return ResponseEntity.ok(userInfo);
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("GET /api/auth/me - Sem contexto de segurança");
                    return Mono.just(ResponseEntity.status(401).<Map<String, Object>>build());
                }));
    }

    /**
     * Endpoint para obter token JWT após autenticação OAuth2.
     * Usado quando o usuário é redirecionado após login.
     *
     * @param token          token vindo na URL (redirecionamento OAuth2), opcional
     * @param authentication autenticação atual (OAuth2) caso precise gerar novo token
     * @return token JWT em caso de admin autenticado ou status apropriado
     */
    @GetMapping("/token")
    public Mono<ResponseEntity<Map<String, String>>> getToken(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String token,
            Authentication authentication) {

        // Se token foi passado na URL (redirecionamento OAuth2)
        if (token != null && !token.isBlank()) {
            Map<String, String> response = new HashMap<>();
            response.put("token", token);
            return Mono.just(ResponseEntity.ok(response));
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        return resolveAdminUser(authentication, null)
                .map(result -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("token", result.token());
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(ex -> {
                    if (ex instanceof UnauthorizedException) {
                        return Mono.just(ResponseEntity.status(401).build());
                    }
                    if (ex instanceof ForbiddenException) {
                        return Mono.just(ResponseEntity.status(403).build());
                    }
                    return Mono.just(ResponseEntity.status(401).build());
                });
    }

    private Mono<AdminUserInfo> resolveAdminUser(Authentication authentication, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    var result = adminAuthorizationService.evaluate(exchange, authentication);

                    if (!result.authenticated()) {
                        throw new UnauthorizedException();
                    }
                    if (!result.admin()) {
                        throw new ForbiddenException();
                    }

                    if (authentication instanceof OAuth2AuthenticationToken tokenAuth) {
                        OAuth2User oauth2User = tokenAuth.getPrincipal();
                        @SuppressWarnings("DataFlowIssue") Map<String, Object> attributes = oauth2User.getAttributes();

                        String email = Optional.ofNullable((String) attributes.get("email"))
                                .filter(s -> !s.isBlank())
                                .orElse((String) attributes.get("login"));
                        String login = (String) attributes.get("login");
                        String name = (String) attributes.get("name");
                        String avatarUrl = (String) attributes.get("avatar_url");

                        String jwtToken = jwtService.generateToken(email, login, true);

                        return new AdminUserInfo(email, login, name, avatarUrl, jwtToken);
                    }

                    if (authentication instanceof UsernamePasswordAuthenticationToken) {
                        if (exchange == null) {
                            throw new UnauthorizedException();
                        }
                        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                            throw new UnauthorizedException();
                        }
                        String token = authHeader.substring("Bearer ".length());
                        if (!jwtService.validateToken(token)) {
                            throw new UnauthorizedException();
                        }

                        String email = jwtService.extractEmail(token);
                        String login = jwtService.extractLogin(token);

                        return new AdminUserInfo(email, login, null, null, token);
                    }

                    log.warn("Tipo de autenticação não suportado: {}", authentication.getClass().getName());
                    throw new UnauthorizedException();
                })
                .onErrorResume(UnauthorizedException.class, Mono::error)
                .onErrorResume(ForbiddenException.class, Mono::error);
    }

    private record AdminUserInfo(String email, String login, String name, String avatarUrl, String token) {
    }

    private static class UnauthorizedException extends RuntimeException {
    }

    private static class ForbiddenException extends RuntimeException {
    }
}

