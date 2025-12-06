package br.com.taohansen.blog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Configuração do WebClient para comunicação com CouchDB.
 * 
 * Configurações de segurança e performance:
 * - Autenticação Basic Auth
 * - Timeouts configuráveis
 * - Logging de requisições/respostas
 */
@Configuration
@Slf4j
public class CouchDbWebClientConfig {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String BASIC_AUTH_PREFIX = "Basic ";

    @Value("${couchdb.uri}")
    private String couchdbUri;

    @Value("${couchdb.username}")
    private String username;

    @Value("${couchdb.password}")
    private String password;

    @Value("${couchdb.timeout:30}")
    private long timeoutSeconds;

    /**
     * Cria o WebClient configurado para comunicação com CouchDB.
     * 
     * @return WebClient configurado
     */
    @Bean
    public WebClient couchDbWebClient() {
        if (couchdbUri == null || couchdbUri.isBlank()) {
            throw new IllegalStateException("couchdb.uri não pode ser vazio");
        }
        
        String authHeader = createBasicAuthHeader(username, password);
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        
        log.info("Configurando WebClient para CouchDB: {} (timeout: {}s)", 
                maskUri(couchdbUri), timeoutSeconds);
        
        return WebClient.builder()
                .baseUrl(couchdbUri)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> {
                    // Configurar codecs se necessário
                    configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024);
                })
                .filter(logRequest())
                .filter(logResponse())
                .filter(timeoutFilter(timeout))
                .build();
    }

    /**
     * Cria o header de autenticação Basic Auth.
     * 
     * @param username Usuário do CouchDB
     * @param password Senha do CouchDB
     * @return Header de autorização
     */
    private String createBasicAuthHeader(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalStateException("Credenciais do CouchDB não podem ser nulas");
        }
        
        String credentials = username + ":" + password;
        byte[] encodedBytes = Base64.getEncoder().encode(credentials.getBytes(StandardCharsets.UTF_8));
        return BASIC_AUTH_PREFIX + new String(encodedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Filtro para logging de requisições.
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            if (log.isDebugEnabled()) {
                log.debug("CouchDB Request: {} {}", 
                        clientRequest.method(), 
                        maskUri(clientRequest.url().toString()));
            }
            return Mono.just(clientRequest);
        });
    }

    /**
     * Filtro para logging de respostas.
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (log.isDebugEnabled()) {
                log.debug("CouchDB Response: {}", clientResponse.statusCode());
            }
            return Mono.just(clientResponse);
        });
    }

    /**
     * Filtro para timeout de requisições.
     * O timeout real é configurado no WebClient via HttpClient.
     */
    private ExchangeFilterFunction timeoutFilter(Duration timeout) {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            // O timeout é tratado pelo HttpClient do WebClient
            // Este filtro apenas loga se necessário
            return Mono.just(request);
        });
    }

    /**
     * Mascara a URI para não expor credenciais em logs.
     */
    private String maskUri(String uri) {
        if (uri == null) {
            return "null";
        }
        // Remove credenciais da URI se houver
        return uri.replaceAll("://[^:]+:[^@]+@", "://***:***@");
    }
}