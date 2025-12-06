package br.com.taohansen.blog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Configuração do WebFlux para desativar completamente recursos estáticos.
 * Esta API é puramente REST e não serve recursos estáticos.
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

    /**
     * Desativa completamente o tratamento de recursos estáticos.
     * Garante que a API seja apenas REST sem servir arquivos estáticos.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Não adiciona nenhum resource handler
        // Isso garante que nenhum recurso estático seja servido
    }
}

