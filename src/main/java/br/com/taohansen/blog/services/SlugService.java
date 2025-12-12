package br.com.taohansen.blog.services;

import br.com.taohansen.blog.repository.CouchDbRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * Serviço responsável pela geração, validação e garantia de unicidade de slugs.
 * Utiliza o {@link CouchDbRepository} para verificar conflitos no armazenamento.
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

    private final CouchDbRepository couchDbRepository;

    /**
     * Valida se um slug é sintaticamente válido.
     *
     * @param slug slug a ser validado
     * @return {@code true} se for válido, {@code false} caso contrário
     */
    public boolean isValidSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return false;
        }

        if (slug.length() > MAX_SLUG_LENGTH) {
            return false;
        }

        return VALID_SLUG_PATTERN.matcher(slug).matches();
    }

    /**
     * Gera um slug único a partir de um título.
     *
     * @param title         título do post
     * @param excludePostId ID do post a ser excluído da verificação (útil para edição, pode ser {@code null})
     * @return {@link Mono} contendo o slug único gerado
     */
    public Mono<String> generateUniqueSlugFromTitle(String title, String excludePostId) {
        if (title == null || title.isBlank()) {
            log.warn("Tentativa de gerar slug com título vazio");
            return Mono.error(new IllegalArgumentException("Título não pode ser vazio"));
        }

        String baseSlug = generateSlugFromTitle(title);
        if (baseSlug.isBlank()) {
            String datePrefix = LocalDate.now().format(DATE_FORMATTER);
            baseSlug = "post-" + datePrefix + "-" + generateShortHash(title);
        }

        return generateUniqueSlug(baseSlug, excludePostId);
    }

    /**
     * Gera um slug único usando estratégias amigáveis para SEO.
     * Prioriza slugs descritivos antes de usar hashes aleatórios.
     * Estratégia em ordem:
     * <ol>
     *     <li>tenta o slug original;</li>
     *     <li>se existir, tenta com número sequencial (ex.: {@code meu-post-2}, {@code meu-post-3}, ...);</li>
     *     <li>se ainda existir, tenta com data (ex.: {@code meu-post-2024-01});</li>
     *     <li>como último recurso, usa um hash curto.</li>
     * </ol>
     *
     * @param baseSlug      slug base (geralmente gerado a partir do título)
     * @param excludePostId ID do post a ser excluído da verificação (útil para edição)
     * @return {@link Mono} contendo o slug único gerado
     */
    public Mono<String> generateUniqueSlug(String baseSlug, String excludePostId) {
        if (baseSlug == null || baseSlug.isBlank()) {
            log.warn("Tentativa de gerar slug único com baseSlug vazio");
            return Mono.error(new IllegalArgumentException("Slug base não pode ser vazio"));
        }

        String finalBaseSlug = sanitizeSlug(baseSlug);

        return couchDbRepository.slugExists(finalBaseSlug, excludePostId)
                .flatMap(exists -> {
                    if (!exists) {
                        log.debug("Slug único gerado: {}", finalBaseSlug);
                        return Mono.just(finalBaseSlug);
                    }

                    log.debug("Slug original '{}' já existe, tentando variações amigáveis para SEO", finalBaseSlug);

                    return trySequentialSlugs(finalBaseSlug, excludePostId, 2)
                            .switchIfEmpty(Mono.defer(() -> {
                                String dateSlug = finalBaseSlug + "-" + LocalDate.now().format(DATE_FORMATTER);
                                return couchDbRepository.slugExists(dateSlug, excludePostId)
                                        .flatMap(dateExists -> {
                                            if (!dateExists) {
                                                log.debug("Slug com data gerado: {}", dateSlug);
                                                return Mono.just(dateSlug);
                                            }
                                            return generateUniqueHashSlug(finalBaseSlug, excludePostId, 0);
                                        });
                            }));
                });
    }

    /**
     * Tenta gerar um slug único usando números sequenciais.
     * <p>
     * Exemplo: {@code meu-post}, {@code meu-post-2}, {@code meu-post-3}, ...
     *
     * @param baseSlug      slug base
     * @param excludePostId ID do post a ser excluído
     * @param startNumber   Número inicial para tentar
     * @return {@link Mono} com o slug único encontrado ou vazio se exceder o limite
     */
    private Mono<String> trySequentialSlugs(String baseSlug, String excludePostId, int startNumber) {
        return Flux.range(startNumber, MAX_SEQUENTIAL_ATTEMPTS)
                .concatMap(number -> {
                    String sequentialSlug = baseSlug + "-" + number;
                    return couchDbRepository.slugExists(sequentialSlug, excludePostId)
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
     * @param baseSlug      slug base
     * @param excludePostId ID do post a ser excluído
     * @param attempt       Número da tentativa (para evitar loop infinito)
     * @return {@link Mono} com o slug único encontrado
     */
    private Mono<String> generateUniqueHashSlug(String baseSlug, String excludePostId, int attempt) {
        if (attempt > MAX_HASH_SLUG_ATTEMPTS) {
            String timestampSlug = baseSlug + "-" + System.currentTimeMillis();
            log.warn("Muitas tentativas de hash, usando timestamp: {}", timestampSlug);
            return Mono.just(timestampSlug);
        }

        String hash = generateShortHash(baseSlug + System.currentTimeMillis() + attempt);
        String hashSlug = baseSlug + "-" + hash;

        return couchDbRepository.slugExists(hashSlug, excludePostId)
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
     *
     * @param title título a ser convertido em slug
     * @return slug base gerado
     */
    public String generateSlugFromTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }

        String normalized = normalizeString(title);

        String slug = normalized
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH);
            slug = slug.replaceAll("-$", "");
        }

        return slug;
    }

    /**
     * Sanitiza um slug removendo caracteres inválidos.
     *
     * @param slug slug a ser sanitizado
     * @return slug sanitizado
     */
    private String sanitizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "";
        }

        return slug
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Gera um hash curto (6 caracteres) a partir de uma string.
     *
     * @param input string de entrada
     * @return hash curto em hexadecimal
     */
    private String generateShortHash(String input) {
        if (input == null || input.isBlank()) {
            input = String.valueOf(System.currentTimeMillis());
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));

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
            String hexHash = Integer.toHexString(input.hashCode());
            return hexHash.substring(0, Math.min(HASH_LENGTH, Math.abs(hexHash.length())));
        }
    }

    /**
     * Normaliza uma string removendo acentos usando Normalizer.
     * <p>
     * Utiliza forma NFD (decomposição canônica) e remove diacríticos.
     *
     * @param str string a ser normalizada
     * @return string normalizada sem acentos
     */
    private String normalizeString(String str) {
        if (str == null) {
            return "";
        }
        String normalized = Normalizer.normalize(str, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}

