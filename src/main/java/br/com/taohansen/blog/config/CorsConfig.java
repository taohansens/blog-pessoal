package br.com.taohansen.blog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuração de CORS (Cross-Origin Resource Sharing).
 * 
 * Permite que aplicações frontend em diferentes origens acessem a API.
 * Configuração segura seguindo melhores práticas.
 */
@Configuration
@Slf4j
public class CorsConfig {

    private static final List<String> ALLOWED_METHODS = Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = Arrays.asList(
            "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"
    );
    private static final long MAX_AGE = 3600L; // 1 hora

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * Configura o filtro CORS para a aplicação.
     * 
     * @return Filtro CORS configurado
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        List<String> origins = parseOrigins(allowedOrigins);
        corsConfig.setAllowedOrigins(origins);
        
        log.info("CORS configurado com origens permitidas: {}", origins);
        
        // Métodos HTTP permitidos
        corsConfig.setAllowedMethods(ALLOWED_METHODS);
        
        // Headers permitidos (não usar "*" por segurança)
        corsConfig.setAllowedHeaders(ALLOWED_HEADERS);
        
        // Headers expostos na resposta
        corsConfig.setExposedHeaders(Arrays.asList("Content-Type", "Content-Length"));
        
        // Tempo de cache do preflight (OPTIONS)
        corsConfig.setMaxAge(MAX_AGE);
        
        // Credenciais só permitidas se não for "*"
        boolean allowCredentials = !origins.contains("*");
        corsConfig.setAllowCredentials(allowCredentials);
        
        if (allowCredentials) {
            log.info("CORS configurado para permitir credenciais");
        } else {
            log.warn("CORS configurado com '*' - credenciais desabilitadas por segurança");
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    /**
     * Parse das origens permitidas a partir da configuração.
     * 
     * @param originsConfig String com origens separadas por vírgula
     * @return Lista de origens permitidas
     */
    private List<String> parseOrigins(String originsConfig) {
        if (originsConfig == null || originsConfig.trim().isEmpty() || "*".equals(originsConfig.trim())) {
            log.warn("CORS configurado com '*' - permitindo todas as origens");
            return List.of("*");
        }
        
        List<String> origins = Stream.of(originsConfig.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toList());
        
        if (origins.isEmpty()) {
            log.warn("Nenhuma origem CORS configurada, usando '*'");
            return List.of("*");
        }
        
        return origins;
    }
}

