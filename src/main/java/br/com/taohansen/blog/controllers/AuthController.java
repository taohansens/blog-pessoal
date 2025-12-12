package br.com.taohansen.blog.controllers;

import br.com.taohansen.blog.security.AdminService;
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
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller para endpoints de autenticação e autorização.
 * Fornece informações sobre o usuário autenticado para o frontend.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AdminService adminService;
    private final JwtService jwtService;

    /**
     * Retorna informações sobre o usuário autenticado (OAuth2 ou JWT).
     *
     * @param exchange contexto da requisição (necessário para ler header Authorization quando JWT)
     * @return informações do usuário ou 401 se não autenticado
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<Map<String, Object>>> getCurrentUser(
            org.springframework.web.server.ServerWebExchange exchange) {
        
        // Ler autenticação do contexto de segurança (funciona mesmo com permitAll)
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> {
                    log.debug("GET /api/auth/me - Authentication: {}, isAuthenticated: {}", 
                            authentication != null ? authentication.getClass().getSimpleName() : "null",
                            authentication != null ? authentication.isAuthenticated() : false);
                    
                    if (authentication == null || !authentication.isAuthenticated()) {
                        log.warn("GET /api/auth/me - Não autenticado");
                        return Mono.just(ResponseEntity.status(401).<Map<String, Object>>build());
                    }
                    
                    return processAuthentication(authentication, exchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("GET /api/auth/me - Sem contexto de segurança");
                    return Mono.just(ResponseEntity.status(401).<Map<String, Object>>build());
                }));
    }
    
    private Mono<ResponseEntity<Map<String, Object>>> processAuthentication(
            Authentication authentication,
            org.springframework.web.server.ServerWebExchange exchange) {

        // Verificar se é autenticação OAuth2 (sessão)
        if (authentication instanceof OAuth2AuthenticationToken token) {
            OAuth2User oauth2User = token.getPrincipal();
            Map<String, Object> attributes = oauth2User.getAttributes();
            
            String email = (String) attributes.get("email");
            if (email == null || email.isBlank()) {
                email = (String) attributes.get("login");
            }
            
            String login = (String) attributes.get("login");
            String name = (String) attributes.get("name");
            String avatarUrl = (String) attributes.get("avatar_url");
            
            boolean isAdmin = adminService.isAdmin(email != null ? email : login);
            
            // Só gerar token JWT se for administrador
            if (!isAdmin) {
                log.warn("Tentativa de obter token por usuário não-admin: {}", email != null ? email : login);
                return Mono.just(ResponseEntity.status(403).build());
            }
            
            // Gerar token JWT apenas para administradores
            String jwtToken = jwtService.generateToken(email, login, true);
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("authenticated", true);
            userInfo.put("email", email);
            userInfo.put("login", login);
            userInfo.put("name", name);
            userInfo.put("avatarUrl", avatarUrl);
            userInfo.put("isAdmin", true);
            userInfo.put("token", jwtToken); // Token JWT assinado
            
            log.debug("Token JWT gerado para administrador via OAuth2: {}", email != null ? email : login);
            
            return Mono.just(ResponseEntity.ok(userInfo));
        }
        
        // Verificar se é autenticação JWT
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            // Extrair informações do token JWT do header
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring("Bearer ".length());
                
                try {
                    if (jwtService.validateToken(token)) {
                        String email = jwtService.extractEmail(token);
                        String login = jwtService.extractLogin(token);
                        Boolean isAdmin = jwtService.extractIsAdmin(token);
                        
                        // Só retornar informações se for admin (tokens JWT só são gerados para admins)
                        if (!Boolean.TRUE.equals(isAdmin)) {
                            log.warn("Tentativa de acesso com token JWT de não-admin");
                            return Mono.just(ResponseEntity.status(403).build());
                        }
                        
                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("authenticated", true);
                        userInfo.put("email", email);
                        userInfo.put("login", login);
                        userInfo.put("isAdmin", true);
                        // Não retornar novo token, usar o mesmo que foi enviado
                        userInfo.put("token", token);
                        
                        log.debug("Informações do usuário retornadas via JWT: {}", email != null ? email : login);
                        
                        return Mono.just(ResponseEntity.ok(userInfo));
                    } else {
                        log.warn("Token JWT inválido ou expirado");
                        return Mono.just(ResponseEntity.status(401).build());
                    }
                } catch (Exception e) {
                    log.error("Erro ao processar token JWT: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(401).build());
                }
            }
        }
        
        log.warn("Tipo de autenticação não suportado: {}", authentication.getClass().getName());
        return Mono.just(ResponseEntity.status(401).build());
    }

    /**
     * Endpoint para obter token JWT após autenticação OAuth2.
     * Usado quando o usuário é redirecionado após login.
     *
     * @param token           token vindo na URL (redirecionamento OAuth2), opcional
     * @param authentication  autenticação atual (OAuth2) caso precise gerar novo token
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
        
        // Se não, verificar autenticação atual e gerar token
        if (authentication != null && authentication.isAuthenticated()) {
            if (authentication instanceof OAuth2AuthenticationToken tokenAuth) {
                OAuth2User oauth2User = tokenAuth.getPrincipal();
                Map<String, Object> attributes = oauth2User.getAttributes();
                
                String email = (String) attributes.get("email");
                if (email == null || email.isBlank()) {
                    email = (String) attributes.get("login");
                }
                
                String login = (String) attributes.get("login");
                boolean isAdmin = adminService.isAdmin(email != null ? email : login);
                
                // Só gerar token JWT se for administrador
                if (!isAdmin) {
                    log.warn("Tentativa de obter token por usuário não-admin: {}", email != null ? email : login);
                    return Mono.just(ResponseEntity.status(403).build());
                }
                
                // Gerar token JWT apenas para administradores
                String jwtToken = jwtService.generateToken(email, login, true);
                
                Map<String, String> response = new HashMap<>();
                response.put("token", jwtToken);
                
                log.debug("Token JWT gerado para administrador via /token: {}", email != null ? email : login);
                
                return Mono.just(ResponseEntity.ok(response));
            }
        }
        
        return Mono.just(ResponseEntity.status(401).build());
    }
}

