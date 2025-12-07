package br.com.taohansen.blog.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Handler de sucesso para autenticação OAuth2.
 * Gera token JWT e redireciona para o frontend com o token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler implements ServerAuthenticationSuccessHandler {

    private final AdminService adminService;
    private final JwtService jwtService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.frontend.auth-callback-path:/auth/callback}")
    private String authCallbackPath;

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        ServerWebExchange exchange = webFilterExchange.getExchange();
        
        try {
            if (authentication instanceof OAuth2AuthenticationToken token) {
                OAuth2User oauth2User = token.getPrincipal();
                Map<String, Object> attributes = oauth2User.getAttributes();
                
                String email = (String) attributes.get("email");
                if (email == null || email.isBlank()) {
                    email = (String) attributes.get("login");
                }
                
                String login = (String) attributes.get("login");
                boolean isAdmin = adminService.isAdmin(email != null ? email : login);
                
                log.info("Autenticação OAuth2 bem-sucedida para: {} (admin: {})", 
                        email != null ? email : login, isAdmin);
                
                // Só gerar token JWT se for administrador
                if (isAdmin) {
                    // Gerar token JWT apenas para administradores
                    String jwtToken = jwtService.generateToken(email, login, true);
                    
                    // Tentar obter a URL original do frontend (se foi passada como parâmetro)
                    String redirectUrl = getRedirectUrl(jwtToken, exchange);
                    
                    log.debug("Redirecionando administrador para: {}", redirectUrl);
                    
                    // Redirecionar para o frontend com o token JWT
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER);
                    exchange.getResponse().getHeaders().setLocation(URI.create(redirectUrl));
                } else {
                    // Usuário não é admin - redirecionar sem token
                    log.warn("Usuário não é administrador: {} - redirecionando sem token", 
                            email != null ? email : login);
                    
                    String redirectUrl = getRedirectUrl(null, exchange);
                    
                    log.debug("Redirecionando usuário não-admin para: {}", redirectUrl);
                    
                    // Redirecionar para o frontend sem token (ou com parâmetro de erro)
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER);
                    exchange.getResponse().getHeaders().setLocation(URI.create(redirectUrl));
                }
                
                return exchange.getResponse().setComplete();
            }
        } catch (Exception e) {
            log.error("Erro no handler de sucesso OAuth2", e);
            // Em caso de erro, redirecionar para o frontend sem token
            // O frontend pode chamar /api/auth/me para obter o token
            try {
                String redirectUrl = frontendUrl + authCallbackPath;
                log.warn("Redirecionando para URL padrão devido a erro: {}", redirectUrl);
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER);
                exchange.getResponse().getHeaders().setLocation(URI.create(redirectUrl));
                return exchange.getResponse().setComplete();
            } catch (Exception ex) {
                log.error("Erro ao redirecionar para URL padrão", ex);
            }
        }
        
        // Se não conseguir processar, deixar o Spring Security fazer o redirecionamento padrão
        log.warn("Nenhuma autenticação OAuth2 encontrada, usando comportamento padrão");
        return webFilterExchange.getChain().filter(exchange);
    }

    /**
     * Obtém a URL de redirecionamento, tentando usar a URL original do frontend
     * ou usando a URL configurada.
     * 
     * @param jwtToken Token JWT (null se não for admin)
     */
    private String getRedirectUrl(String jwtToken, ServerWebExchange exchange) {
        // Tentar obter a URL original do parâmetro 'redirect_uri'
        String redirectUri = exchange.getRequest().getQueryParams().getFirst("redirect_uri");
        
        // Também tentar obter do header Referer ou Origin
        if (redirectUri == null || redirectUri.isBlank()) {
            String referer = exchange.getRequest().getHeaders().getFirst("Referer");
            if (referer != null && !referer.isBlank()) {
                // Extrair a origem do referer
                try {
                    URI refererUri = URI.create(referer);
                    redirectUri = refererUri.getScheme() + "://" + refererUri.getAuthority() + authCallbackPath;
                } catch (Exception e) {
                    log.debug("Não foi possível extrair URL do Referer: {}", e.getMessage());
                }
            }
        }
        
        String baseUrl;
        if (redirectUri != null && !redirectUri.isBlank() && isValidFrontendUrl(redirectUri)) {
            baseUrl = redirectUri;
        } else {
            baseUrl = frontendUrl + authCallbackPath;
        }
        
        // Se tiver token, adicionar na URL
        if (jwtToken != null && !jwtToken.isBlank()) {
            String separator = baseUrl.contains("?") ? "&" : "?";
            return baseUrl + separator + "token=" + URLEncoder.encode(jwtToken, StandardCharsets.UTF_8);
        } else {
            // Se não for admin, adicionar parâmetro indicando que não é admin
            String separator = baseUrl.contains("?") ? "&" : "?";
            return baseUrl + separator + "error=not_admin";
        }
    }

    /**
     * Valida se a URL é do frontend permitido.
     */
    private boolean isValidFrontendUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        
        // Permitir URLs que começam com a URL do frontend configurada
        if (url.startsWith(frontendUrl)) {
            return true;
        }
        
        // Permitir localhost para desenvolvimento
        if (url.startsWith("http://localhost") || url.startsWith("https://localhost")) {
            return true;
        }
        
        // Permitir URLs HTTPS em produção
        if (url.startsWith("https://")) {
            return true;
        }
        
        return false;
    }
}

