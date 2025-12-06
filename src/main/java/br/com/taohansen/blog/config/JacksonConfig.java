package br.com.taohansen.blog.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuração do Jackson (serialização/deserialização JSON).
 * 
 * Configurações otimizadas para API REST:
 * - Suporte a Java 8+ Time API
 * - Ignora propriedades desconhecidas (flexibilidade)
 * - Formatação de datas como ISO-8601
 */
@Configuration
public class JacksonConfig {
    
    /**
     * Configura o ObjectMapper principal da aplicação.
     * 
     * @return ObjectMapper configurado
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Suporte a Java 8+ Time API (LocalDate, LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());
        
        // Datas como ISO-8601 strings ao invés de timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Ignora propriedades desconhecidas (evita erros em versões futuras)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // Não falha em propriedades ignoradas
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        
        // Não falha em valores null para tipos primitivos
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        
        return mapper;
    }
}
