package br.com.taohansen.blog.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import reactor.core.publisher.Flux;

/**
 * Serviço responsável pela geração e validação de slugs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SlugService {

    private static final int MAX_SLUG_LENGTH = 200;
    private static final int MIN_SLUG_LENGTH = 1;
    private static final Pattern VALID_SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int HASH_LENGTH = 6;
    private static final int MAX_HASH_SLUG_ATTEMPTS = 5;
    private static final int MAX_SEQUENTIAL_ATTEMPTS = 50;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CouchDbService couchDbService;

    /**
     * Valida se um slug é válido.
     * @param slug O slug a ser validado
     * @return true se válido, false caso contrário
     */
    public boolean isValidSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return false;
        }
        
        if (slug.length() < MIN_SLUG_LENGTH || slug.length() > MAX_SLUG_LENGTH) {
            return false;
        }
        
        return VALID_SLUG_PATTERN.matcher(slug).matches();
    }

    /**
     * Gera um slug único a partir de um título.
     * Primeiro gera o slug base do título, depois verifica se é único e adiciona hash se necessário.
     * @param title O título do post
     * @param excludePostId ID do post a ser excluído da verificação (útil para edição, pode ser null)
     * @return Mono<String> O slug único gerado
     */
    public Mono<String> generateUniqueSlugFromTitle(String title, String excludePostId) {
        if (title == null || title.isBlank()) {
            log.warn("Tentativa de gerar slug com título vazio");
            return Mono.error(new IllegalArgumentException("Título não pode ser vazio"));
        }

        String baseSlug = generateSlugFromTitle(title);
        if (baseSlug.isBlank()) {
            // Se não conseguir gerar slug do título, usar data + hash curto
            String datePrefix = LocalDate.now().format(DATE_FORMATTER);
            baseSlug = "post-" + datePrefix + "-" + generateShortHash(title);
        }
        
        return generateUniqueSlug(baseSlug, excludePostId);
    }

    /**
     * Gera um slug único usando estratégias amigáveis para SEO.
     * Prioriza slugs descritivos antes de usar hashes aleatórios.
     * 
     * Estratégia:
     * 1. Tenta o slug original
     * 2. Se existir, tenta com número sequencial (meu-post-2, meu-post-3, ...)
     * 3. Se ainda existir, tenta com data (meu-post-2024-01)
     * 4. Como último recurso, usa hash curto
     * 
     * @param baseSlug O slug base (geralmente gerado a partir do título)
     * @param excludePostId ID do post a ser excluído da verificação (útil para edição)
     * @return Mono<String> O slug único gerado
     */
    public Mono<String> generateUniqueSlug(String baseSlug, String excludePostId) {
        if (baseSlug == null || baseSlug.isBlank()) {
            log.warn("Tentativa de gerar slug único com baseSlug vazio");
            return Mono.error(new IllegalArgumentException("Slug base não pode ser vazio"));
        }

        // Sanitizar o slug base
        String finalBaseSlug = sanitizeSlug(baseSlug);

        return couchDbService.slugExists(finalBaseSlug, excludePostId)
                .flatMap(exists -> {
                    if (!exists) {
                        log.debug("Slug único gerado: {}", finalBaseSlug);
                        return Mono.just(finalBaseSlug);
                    }
                    
                    // Slug existe, tentar estratégias amigáveis para SEO
                    log.debug("Slug original '{}' já existe, tentando variações amigáveis para SEO", finalBaseSlug);
                    
                    // Estratégia 1: Tentar com número sequencial
                    return trySequentialSlugs(finalBaseSlug, excludePostId, 2)
                            .switchIfEmpty(Mono.defer(() -> {
                                // Estratégia 2: Tentar com data
                                String dateSlug = finalBaseSlug + "-" + LocalDate.now().format(DATE_FORMATTER);
                                return couchDbService.slugExists(dateSlug, excludePostId)
                                        .flatMap(dateExists -> {
                                            if (!dateExists) {
                                                log.debug("Slug com data gerado: {}", dateSlug);
                                                return Mono.just(dateSlug);
                                            }
                                            // Estratégia 3: Usar hash como último recurso (verificar se é único)
                                            return generateUniqueHashSlug(finalBaseSlug, excludePostId, 0);
                                        });
                            }));
                });
    }
    
    /**
     * Tenta gerar um slug único usando números sequenciais.
     * Exemplo: meu-post, meu-post-2, meu-post-3, ...
     * Usa iteração reativa ao invés de recursão para evitar stack overflow.
     * 
     * @param baseSlug O slug base
     * @param excludePostId ID do post a ser excluído
     * @param startNumber Número inicial para tentar
     * @return Mono com o slug único encontrado ou vazio se exceder o limite
     */
    private Mono<String> trySequentialSlugs(String baseSlug, String excludePostId, int startNumber) {
        return Flux.range(startNumber, MAX_SEQUENTIAL_ATTEMPTS)
                .concatMap(number -> {
                    String sequentialSlug = baseSlug + "-" + number;
                    return couchDbService.slugExists(sequentialSlug, excludePostId)
                            .flatMap(exists -> {
                                if (!exists) {
                                    log.debug("Slug sequencial gerado: {}", sequentialSlug);
                                    return Mono.just(sequentialSlug);
                                }
                                return Mono.empty();
                            });
                })
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Limite de tentativas sequenciais excedido para slug: {}", baseSlug);
                    return Mono.empty();
                }));
    }
    
    /**
     * Gera um slug único usando hash como último recurso.
     * Tenta diferentes hashes até encontrar um único.
     * 
     * @param baseSlug O slug base
     * @param excludePostId ID do post a ser excluído
     * @param attempt Número da tentativa (para evitar loop infinito)
     * @return Mono com o slug único encontrado
     */
    private Mono<String> generateUniqueHashSlug(String baseSlug, String excludePostId, int attempt) {
        if (attempt > MAX_HASH_SLUG_ATTEMPTS) {
            // Se exceder MAX_HASH_SLUG_ATTEMPTS, usar timestamp completo
            String timestampSlug = baseSlug + "-" + System.currentTimeMillis();
            log.warn("Muitas tentativas de hash, usando timestamp: {}", timestampSlug);
            return Mono.just(timestampSlug);
        }
        
        String hash = generateShortHash(baseSlug + System.currentTimeMillis() + attempt);
        String hashSlug = baseSlug + "-" + hash;
        
        return couchDbService.slugExists(hashSlug, excludePostId)
                .flatMap(exists -> {
                    if (!exists) {
                        log.debug("Slug com hash gerado (último recurso): {}", hashSlug);
                        return Mono.just(hashSlug);
                    }
                    // Tentar novamente com hash diferente
                    return generateUniqueHashSlug(baseSlug, excludePostId, attempt + 1);
                });
    }

    /**
     * Gera um slug base a partir de um título.
     * Remove acentos, converte para minúsculas, substitui espaços por hífens e remove caracteres especiais.
     * @param title O título a ser convertido em slug
     * @return O slug base gerado
     */
    public String generateSlugFromTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        
        // Normalizar: remover acentos, converter para minúsculas
        String normalized = normalizeString(title);
        
        // Substituir espaços e caracteres especiais por hífen
        String slug = normalized
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "") // Remove caracteres especiais exceto espaços e hífens
                .replaceAll("\\s+", "-") // Substitui espaços múltiplos por um único hífen
                .replaceAll("-+", "-") // Remove hífens múltiplos
                .replaceAll("^-|-$", ""); // Remove hífens no início e fim
        
        // Limitar tamanho máximo
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH);
            // Remover hífen no final se houver
            slug = slug.replaceAll("-$", "");
        }
        
        return slug;
    }

    /**
     * Sanitiza um slug removendo caracteres inválidos.
     * @param slug O slug a ser sanitizado
     * @return Slug sanitizado
     */
    private String sanitizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "";
        }
        
        return slug
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9-]", "") // Remove caracteres inválidos
                .replaceAll("-+", "-") // Remove hífens múltiplos
                .replaceAll("^-|-$", ""); // Remove hífens no início e fim
    }

    /**
     * Gera um hash curto (6 caracteres) a partir de uma string.
     * @param input A string de entrada
     * @return Hash curto em hexadecimal
     */
    private String generateShortHash(String input) {
        if (input == null || input.isBlank()) {
            input = String.valueOf(System.currentTimeMillis());
        }
        
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Converter para hexadecimal e pegar os primeiros 6 caracteres
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.substring(0, Math.min(HASH_LENGTH, hexString.length()));
        } catch (NoSuchAlgorithmException e) {
            log.error("Erro ao gerar hash: {}", e.getMessage(), e);
            // Fallback: usar hash simples baseado no hashCode
            String hexHash = Integer.toHexString(input.hashCode());
            return hexHash.substring(0, Math.min(HASH_LENGTH, Math.abs(hexHash.length())));
        }
    }

    /**
     * Normaliza uma string removendo acentos usando Normalizer.
     * @param str A string a ser normalizada
     * @return String normalizada sem acentos
     */
    private String normalizeString(String str) {
        if (str == null) {
            return "";
        }
        // NFD = Canonical Decomposition, remove diacríticos
        String normalized = Normalizer.normalize(str, Normalizer.Form.NFD);
        // Remove caracteres Unicode de diacríticos (acentos)
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}

