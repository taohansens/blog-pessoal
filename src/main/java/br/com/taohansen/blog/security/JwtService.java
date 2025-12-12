package br.com.taohansen.blog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Serviço para geração e validação de tokens JWT.
 * Os tokens são assinados com uma chave secreta e contêm informações do usuário.
 */
@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret:mySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLong}")
    private String jwtSecret;

    @Value("${app.jwt.expiration:86400000}") // 24 horas em milissegundos
    private long jwtExpiration;

    /**
     * Gera um token JWT para o usuário autenticado.
     * 
     * @param email Email do usuário
     * @param login Login do usuário
     * @param isAdmin Se o usuário é administrador
     * @return Token JWT
     */
    public String generateToken(String email, String login, boolean isAdmin) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("login", login);
        claims.put("isAdmin", isAdmin);
        
        return createToken(claims, email != null ? email : login);
    }

    /**
     * Cria o token JWT com os claims fornecidos.
     *
     * @param claims  claims a serem incluídos
     * @param subject identificador principal (email/login)
     * @return token JWT assinado
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtExpiration);
        
        SecretKey key = getSigningKey();
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    /**
     * Valida se o token é válido (assinatura e expiração).
     * 
     * @param token Token JWT
     * @return true se válido, false caso contrário
     */
    public boolean validateToken(String token) {
        try {
            if (token == null || token.isBlank()) {
                log.debug("Token nulo ou vazio");
                return false;
            }
            
            // Verificar assinatura e extrair claims
            Claims claims = extractAllClaims(token);
            
            // Verificar expiração diretamente das claims
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                log.debug("Token expirado em: {}", expiration);
                return false;
            }
            
            log.debug("Token JWT válido");
            return true;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.warn("Assinatura do token inválida: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("Token expirado: {}", e.getMessage());
            return false;
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.warn("Token malformado: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Erro ao validar token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extrai o email/login do token.
     *
     * @param token JWT
     * @return subject (email/login) do token
     */
    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrai se o usuário é administrador do token.
     *
     * @param token JWT
     * @return {@code true} se admin, {@code false} caso contrário
     */
    public Boolean extractIsAdmin(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("isAdmin", Boolean.class);
    }

    /**
     * Extrai o email do token.
     *
     * @param token JWT
     * @return email contido no token
     */
    public String extractEmail(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Extrai o login do token.
     *
     * @param token JWT
     * @return login contido no token
     */
    public String extractLogin(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("login", String.class);
    }

    /**
     * Extrai uma claim específica do token.
     *
     * @param token           JWT
     * @param claimsResolver  função que lê a claim
     * @return valor da claim resolvida
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extrai todas as claims do token.
     *
     * @param token JWT
     * @return claims do token
     */
    private Claims extractAllClaims(String token) {
        SecretKey key = getSigningKey();
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Verifica se o token está expirado.
     *
     * @param token JWT
     * @return {@code true} se expirado
     */
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extrai a data de expiração do token.
     *
     * @param token JWT
     * @return data de expiração
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Obtém a chave de assinatura.
     *
     * @return chave HMAC baseada no segredo configurado
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        // Garantir que a chave tenha pelo menos 256 bits (32 bytes)
        if (keyBytes.length < 32) {
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
            keyBytes = paddedKey;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

