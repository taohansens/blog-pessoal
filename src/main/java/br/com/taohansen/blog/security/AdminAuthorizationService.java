package br.com.taohansen.blog.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

/**
 * Serviço centralizado para verificação de privilégios de administrador.
 * <p>
 * Encapsula a lógica de interpretação da {@link Authentication} (OAuth2 ou JWT),
 * consulta ao {@link AdminService} e ao {@link JwtService} e retorna um
 * {@link AdminAuthResult} com informações consolidadas sobre autenticação e permissão.
 * É utilizado por filtros e controllers para evitar duplicação de código.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthorizationService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AdminService adminService;
    private final JwtService jwtService;

    /**
     * Avalia o contexto de autenticação atual e determina se o usuário é administrador.
     *
     * @param exchange       contexto da requisição (usado para ler cabeçalho Authorization em caso de JWT)
     * @param authentication autenticação atual do Spring Security
     * @return resultado contendo flags de autenticação, privilégio de admin e identificador do usuário
     */
    public AdminAuthResult evaluate(ServerWebExchange exchange, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return new AdminAuthResult(false, false, null);
        }

        boolean isAdmin = false;
        String userIdentifier = null;

        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            OAuth2User oauth2User = oauth2Token.getPrincipal();
            if (oauth2User != null) {
                userIdentifier = getEmailFromOAuth2User(oauth2User);
                isAdmin = adminService.isAdmin(userIdentifier);
            }
        } else if (authentication instanceof UsernamePasswordAuthenticationToken) {
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

        log.debug("Resultado de verificação de admin - usuário: {}, autenticado: {}, é admin: {}",
                userIdentifier, true, isAdmin);

        return new AdminAuthResult(true, isAdmin, userIdentifier);
    }

    /**
     * Obtém o e-mail (ou login) de um {@link OAuth2User}, com fallback para o atributo {@code login}.
     *
     * @param oauth2User usuário autenticado via OAuth2
     * @return e-mail ou login associado ao usuário, ou {@code null} se não encontrado
     */
    private String getEmailFromOAuth2User(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        if (email == null || email.isBlank()) {
            email = oauth2User.getAttribute("login");
        }
        return email;
    }

    /**
     * Resultado da verificação de privilégios de administrador.
     *
     * @param authenticated indica se havia autenticação válida
     * @param admin         indica se o usuário possui privilégio de administrador
     * @param userIdentifier identificador (email/login) usado na verificação
     */
    public record AdminAuthResult(boolean authenticated, boolean admin, String userIdentifier) {
    }
}



