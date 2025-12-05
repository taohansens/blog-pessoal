package br.com.taohansen.blog.services;

import br.com.taohansen.blog.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"unchecked", "NullableProblems"})
@Service
@RequiredArgsConstructor
@Slf4j
public class CouchDbService {

    @Value("${couchdb.database:blog}")
    private String databaseName;
    
    private final WebClient couchDbWebClient;

    public Flux<Post> listPosts() {
        String uri = String.format("/%s/_design/posts/_view/by_date?include_docs=true", databaseName);
        
        return couchDbWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(PostsViewResponse.class)
                .flatMapMany(response -> {
                    log.info("Posts encontrados: {}", response.getRows().size());

                    return Flux.fromIterable(response.getRows())
                            .filter(row -> row.getDoc() != null)
                            .map(this::mapToPost)
                            .sort((p1, p2) -> p2.getDate().compareTo(p1.getDate()));
                })
                .doOnNext(post -> log.debug("Post mapeado: {}", post.getTitle()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("Erro ao buscar posts do CouchDB: {}", ex.getMessage(), ex);
                    return Flux.error(ex);
                });
    }

    public Mono<Post> getPostBySlug(String slug) {
        String uri = String.format("/%s/_design/posts/_view/by_date?include_docs=true", databaseName);
        
        return couchDbWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(PostsViewResponse.class)
                .flatMapMany(response -> {
                    log.debug("Buscando post com slug: {}", slug);
                    return Flux.fromIterable(response.getRows())
                            .filter(row -> row.getDoc() != null)
                            .map(this::mapToPost)
                            .filter(post -> slug.equals(post.getSlug()));
                })
                .next()
                .doOnNext(post -> log.info("Post carregado: {}", post.getTitle()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Post não encontrado com slug: {}", slug);
                    return Mono.empty();
                }))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("Erro ao buscar post do CouchDB: {}", ex.getMessage(), ex);
                    return Mono.error(ex);
                });
    }

    private Post mapToPost(PostsViewResponse.Row row) {
        Map<String, Object> doc = row.getDoc();

        Post post = new Post();
        mapCommonFieldsToPost(doc, post);
        post.setContent((String) doc.get("content"));
        return post;
    }
    
    private void mapCommonFieldsToPost(Map<String, Object> doc, Post post) {
        post.setId((String) doc.get("id"));
        post.setTitle((String) doc.get("title"));
        post.setSlug((String) doc.get("slug"));
        post.setSummary((String) doc.get("summary"));
        post.setTags((List<String>) doc.get("tags"));
        
        String dateStr = (String) doc.get("date");
        if (dateStr != null) {
            post.setDate(LocalDate.parse(dateStr));
        }
    }
    
    private void mapCommonFieldsToMetadata(Map<String, Object> doc, PostMetadata meta) {
        meta.setId((String) doc.get("id"));
        meta.setTitle((String) doc.get("title"));
        meta.setSlug((String) doc.get("slug"));
        meta.setSummary((String) doc.get("summary"));
        meta.setTags((List<String>) doc.get("tags"));
        
        String dateStr = (String) doc.get("date");
        if (dateStr != null) {
            meta.setDate(LocalDate.parse(dateStr));
        }
    }

    public Mono<PagedPostsResponse> listPostsPaged(int page, int size) {
        int skip = page * size;
        String uri = String.format("/%s/_design/posts/_view/by_date?include_docs=true&skip=%d&limit=%d", 
                databaseName, skip, size);

        return couchDbWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(PostsViewResponse.class)
                .map(response -> {
                    List<PostMetadata> metadataList = response.getRows().stream()
                            .filter(row -> row.getDoc() != null)
                            .map(this::mapToMetadata)
                            .toList();

                    long totalRows = response.getTotalRows();
                    PagedPostsResponse paged = new PagedPostsResponse();
                    paged.setPosts(metadataList);
                    paged.setPage(page);
                    paged.setSize(size);
                    paged.setTotal(totalRows);
                    paged.setHasNext((long) (page + 1) * size < totalRows);
                    return paged;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("Erro ao buscar posts paginados do CouchDB: {}", ex.getMessage(), ex);
                    return Mono.error(ex);
                });
    }

    private PostMetadata mapToMetadata(PostsViewResponse.Row row) {
        Map<String, Object> doc = row.getDoc();
        PostMetadata meta = new PostMetadata();
        mapCommonFieldsToMetadata(doc, meta);
        return meta;
    }

    /**
     * Verifica se um slug já existe no banco de dados.
     * @param slug O slug a ser verificado
     * @param excludePostId ID do post a ser excluído da verificação (útil para edição)
     * @return Mono<Boolean> true se o slug existe, false caso contrário
     */
    public Mono<Boolean> slugExists(String slug, String excludePostId) {
        return getPostBySlug(slug)
                .map(post -> {
                    // Se estamos editando um post, ignorar se o slug pertence ao mesmo post
                    if (excludePostId != null && excludePostId.equals(post.getId())) {
                        return false;
                    }
                    return true;
                })
                .defaultIfEmpty(false);
    }

    /**
     * Gera um slug único a partir de um título.
     * Primeiro gera o slug base do título, depois verifica se é único e adiciona hash se necessário.
     * @param title O título do post
     * @param excludePostId ID do post a ser excluído da verificação (útil para edição, pode ser null)
     * @return Mono<String> O slug único gerado
     */
    public Mono<String> generateUniqueSlugFromTitle(String title, String excludePostId) {
        String baseSlug = generateSlugFromTitle(title);
        if (baseSlug.isBlank()) {
            // Se não conseguir gerar slug do título, usar hash do título completo
            baseSlug = "post-" + generateShortHash(title != null ? title : String.valueOf(System.currentTimeMillis()));
        }
        return generateUniqueSlug(baseSlug, excludePostId);
    }

    /**
     * Gera um slug único. Se o slug base já existir, adiciona um hash curto no final.
     * @param baseSlug O slug base (geralmente gerado a partir do título)
     * @param excludePostId ID do post a ser excluído da verificação (útil para edição)
     * @return Mono<String> O slug único gerado
     */
    public Mono<String> generateUniqueSlug(String baseSlug, String excludePostId) {
        return slugExists(baseSlug, excludePostId)
                .flatMap(exists -> {
                    if (!exists) {
                        log.debug("Slug único gerado: {}", baseSlug);
                        return Mono.just(baseSlug);
                    }
                    
                    // Slug existe, gerar um novo com hash
                    String hash = generateShortHash(baseSlug);
                    String uniqueSlug = baseSlug + "-" + hash;
                    
                    log.debug("Slug original '{}' já existe, gerando slug único: {}", baseSlug, uniqueSlug);
                    
                    // Verificar recursivamente se o slug com hash também existe (improvável, mas possível)
                    return slugExists(uniqueSlug, excludePostId)
                            .flatMap(hashExists -> {
                                if (!hashExists) {
                                    return Mono.just(uniqueSlug);
                                }
                                // Se por acaso o hash também existir, adicionar timestamp
                                String timestampHash = generateShortHash(baseSlug + System.currentTimeMillis());
                                return Mono.just(baseSlug + "-" + timestampHash);
                            });
                });
    }

    /**
     * Gera um hash curto (6 caracteres) a partir de uma string.
     * @param input A string de entrada
     * @return Hash curto em hexadecimal
     */
    private String generateShortHash(String input) {
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
            
            return hexString.substring(0, 6);
        } catch (NoSuchAlgorithmException e) {
            log.error("Erro ao gerar hash: {}", e.getMessage(), e);
            // Fallback: usar hash simples baseado no hashCode
            return Integer.toHexString(input.hashCode()).substring(0, Math.min(6, Integer.toHexString(input.hashCode()).length()));
        }
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
        
        return slug;
    }

    /**
     * Normaliza uma string removendo acentos.
     * @param str A string a ser normalizada
     * @return String normalizada sem acentos
     */
    private String normalizeString(String str) {
        if (str == null) {
            return "";
        }
        
        // Mapeamento básico de acentos (pode ser expandido)
        return str
                .replace("á", "a").replace("à", "a").replace("ã", "a").replace("â", "a").replace("ä", "a")
                .replace("é", "e").replace("è", "e").replace("ê", "e").replace("ë", "e")
                .replace("í", "i").replace("ì", "i").replace("î", "i").replace("ï", "i")
                .replace("ó", "o").replace("ò", "o").replace("õ", "o").replace("ô", "o").replace("ö", "o")
                .replace("ú", "u").replace("ù", "u").replace("û", "u").replace("ü", "u")
                .replace("ç", "c")
                .replace("ñ", "n");
    }
}
