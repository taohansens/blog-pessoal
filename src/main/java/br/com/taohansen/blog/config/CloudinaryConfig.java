package br.com.taohansen.blog.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configura e expõe o cliente Cloudinary como bean singleton.
 * <p>
 * Responsável por validar propriedades obrigatórias e garantir comunicação segura
 * (HTTPS) com o provedor.
 */
@Configuration
public class CloudinaryConfig {

    /**
     * Cria e configura o cliente Cloudinary usado pelos serviços de mídia.
     *
     * @param cloudName nome do cloud configurado no Cloudinary
     * @param apiKey    chave pública do Cloudinary
     * @param apiSecret chave privada do Cloudinary
     * @return instância configurada de {@link Cloudinary}
     */
    @Bean
    public Cloudinary cloudinary(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {

        if (cloudName == null || cloudName.isBlank()
                || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("Configurações do Cloudinary ausentes. Defina cloudinary.cloud-name, cloudinary.api-key e cloudinary.api-secret.");
        }

        Cloudinary cloudinary = new Cloudinary(Map.of(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));

        cloudinary.config.secure = true;
        return cloudinary;
    }
}

