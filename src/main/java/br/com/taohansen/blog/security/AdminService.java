package br.com.taohansen.blog.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serviço para verificação de administrador.
 * Evita dependência circular entre SecurityConfig e AdminAuthorizationFilter.
 */
@Service
@Slf4j
public class AdminService {

    @Value("${app.admin.email}")
    private String adminEmail;

    /**
     * Verifica se o usuario é do administrador.
     * 
     * @param email Email a ser verificado
     * @return true se for administrador, false caso contrário
     */
    public boolean isAdmin(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return adminEmail.equalsIgnoreCase(email.trim());
    }
}

