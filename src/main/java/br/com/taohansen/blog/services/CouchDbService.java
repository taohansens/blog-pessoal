package br.com.taohansen.blog.services;

import br.com.taohansen.blog.models.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Serviço responsável pela comunicação com o CouchDB.
 * Foca exclusivamente em operações de leitura/escrita no banco de dados.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouchDbService {

    private static final String VIEW_BY_DATE = "_design/posts/_view/by_date";
    private static final String VIEW_BY_SLUG = "_design/posts/_view/by_slug";
    private static final String PARAM_INCLUDE_DOCS = "include_docs=true";
    private static final String PARAM_SKIP = "skip";
    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_KEY = "key";
    
    // Validações de segurança
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_PAGE = 0;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_SLUG_LENGTH = 200;

    @Value("${couchdb.database:blog}")
    private String databaseName;
    
    private final WebClient couchDbWebClient;
    private final PostMapper postMapper;

    /**
     * Lista todos os posts ordenados por data (mais recentes primeiro).
     * A ordenação é feita pela view do CouchDB (descending), otimizando performance.
     * @return Flux de posts
     */
    public Flux<Post> listPosts() {
        String uri = buildViewUri(VIEW_BY_DATE, PARAM_INCLUDE_DOCS);
        
        return couchDbWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(PostsViewResponse.class)
                .flatMapMany(response -> {
                    log.info("Posts encontrados: {}", response.getRows().size());

                    return Flux.fromIterable(response.getRows())
                            .filter(row -> row.getDoc() != null)
                            .map(postMapper::mapToPost)
                            .filter(post -> post != null);
                })
                .doOnNext(post -> log.debug("Post mapeado: {}", post.getTitle()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("Erro ao buscar posts do CouchDB. Status: {}, Mensagem: {}", 
                            ex.getStatusCode(), ex.getMessage());
                    return Flux.error(new RuntimeException("Erro ao buscar posts", ex));
                });
    }

    /**
     * Busca um post pelo slug.
     * Tenta usar uma view específica por slug se disponível, caso contrário busca em todos os posts.
     * @param slug O slug do post (validado e sanitizado)
     * @return Mono com o post encontrado ou vazio se não encontrado
     */
    public Mono<Post> getPostBySlug(String slug) {
        // Validação de entrada
        if (slug == null || slug.isBlank()) {
            log.warn("Tentativa de buscar post com slug vazio ou nulo");
            return Mono.error(new IllegalArgumentException("Slug não pode ser vazio"));
        }
        
        // Sanitização e validação de segurança
        String sanitizedSlug = sanitizeSlug(slug);
        if (sanitizedSlug.length() > MAX_SLUG_LENGTH) {
            log.warn("Slug muito longo: {}", sanitizedSlug.length());
            return Mono.error(new IllegalArgumentException("Slug excede tamanho máximo permitido"));
        }
        
        // Tentar usar view específica por slug
        String uriBySlug = buildViewUriWithKey(VIEW_BY_SLUG, sanitizedSlug, PARAM_INCLUDE_DOCS);
        
        return couchDbWebClient.get()
                .uri(uriBySlug)
                .retrieve()
                .bodyToMono(PostsViewResponse.class)
                .flatMapMany(response -> Flux.fromIterable(response.getRows())
                        .filter(row -> row.getDoc() != null)
                        .map(postMapper::mapToPost)
                        .filter(post -> post != null && sanitizedSlug.equals(post.getSlug())))
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    // Fallback: buscar em todos os posts se view por slug não existir
                    log.debug("View por slug não retornou resultados, tentando busca completa para slug: {}", sanitizedSlug);
                    return searchPostBySlugInAllPosts(sanitizedSlug);
                }))
                .doOnNext(post -> log.info("Post carregado: {}", post.getTitle()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    int statusCode = ex.getStatusCode().value();
                    // 404 = view não existe, 400 = view não existe ou query inválida
                    if (statusCode == 404 || statusCode == 400) {
                        // View não existe ou query inválida, usar fallback
                        log.debug("View por slug não disponível (status: {}), usando busca completa", statusCode);
                        return searchPostBySlugInAllPosts(sanitizedSlug);
                    }
                    log.error("Erro ao buscar post do CouchDB. Status: {}, Slug: {}", 
                            ex.getStatusCode(), sanitizedSlug);
                    return Mono.error(new RuntimeException("Erro ao buscar post", ex));
                });
    }
    
    /**
     * Busca um post pelo slug em todos os posts (fallback quando view específica não existe).
     * @param slug O slug sanitizado
     * @return Mono com o post encontrado ou vazio
     */
    private Mono<Post> searchPostBySlugInAllPosts(String slug) {
        String uri = buildViewUri(VIEW_BY_DATE, PARAM_INCLUDE_DOCS);
        
        return couchDbWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(PostsViewResponse.class)
                .flatMapMany(response -> Flux.fromIterable(response.getRows())
                        .filter(row -> row.getDoc() != null)
                        .map(postMapper::mapToPost)
                        .filter(post -> post != null && slug.equals(post.getSlug())))
                .next()
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Post não encontrado com slug: {}", slug);
                    return Mono.empty();
                }));
    }


    /**
     * Lista posts paginados.
     * @param page Número da página (0-indexed, validado)
     * @param size Tamanho da página (validado entre MIN_PAGE_SIZE e MAX_PAGE_SIZE)
     * @return Mono com resposta paginada
     */
    public Mono<PagedPostsResponse> listPostsPaged(int page, int size) {
        // Validação de entrada
        if (page < MIN_PAGE) {
            log.warn("Página inválida: {}", page);
            return Mono.error(new IllegalArgumentException("Página deve ser >= 0"));
        }
        
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            log.warn("Tamanho de página inválido: {}", size);
            return Mono.error(new IllegalArgumentException(
                    String.format("Tamanho da página deve estar entre %d e %d", MIN_PAGE_SIZE, MAX_PAGE_SIZE)));
        }
        
        int skip = page * size;
        String uri = buildViewUriWithPagination(VIEW_BY_DATE, skip, size, PARAM_INCLUDE_DOCS);

        return couchDbWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(PostsViewResponse.class)
                .map(response -> {
                    List<PostMetadata> metadataList = response.getRows().stream()
                            .filter(row -> row.getDoc() != null)
                            .map(postMapper::mapToMetadata)
                            .filter(meta -> meta != null)
                            .toList();

                    long totalRows = response.getTotalRows();
                    return PagedPostsResponse.builder()
                            .posts(metadataList)
                            .page(page)
                            .size(size)
                            .total(totalRows)
                            .hasNext((long) (page + 1) * size < totalRows)
                            .build();
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("Erro ao buscar posts paginados do CouchDB. Status: {}, Página: {}, Tamanho: {}", 
                            ex.getStatusCode(), page, size);
                    return Mono.error(new RuntimeException("Erro ao buscar posts paginados", ex));
                });
    }

    /**
     * Verifica se um slug já existe no banco de dados.
     * Otimizado para usar view específica ao invés de buscar post completo.
     * @param slug O slug a ser verificado (validado e sanitizado)
     * @param excludePostId ID do post a ser excluído da verificação (útil para edição)
     * @return Mono<Boolean> true se o slug existe, false caso contrário
     */
    public Mono<Boolean> slugExists(String slug, String excludePostId) {
        if (slug == null || slug.isBlank()) {
            return Mono.just(false);
        }
        
        String sanitizedSlug = sanitizeSlug(slug);
        
        // Tentar usar view específica por slug
        String uriBySlug = buildViewUriWithKey(VIEW_BY_SLUG, sanitizedSlug);
        
        return couchDbWebClient.get()
                .uri(uriBySlug)
                .retrieve()
                .bodyToMono(PostsViewResponse.class)
                .map(response -> {
                    if (response.getRows() == null || response.getRows().isEmpty()) {
                        return false;
                    }
                    
                    // Verificar se o slug pertence a outro post (não o que está sendo editado)
                    return response.getRows().stream()
                            .filter(row -> row.getDoc() != null)
                            .anyMatch(row -> {
                                // Buscar ID - pode ser "id" (em views) ou "_id" (em documentos diretos)
                                String postId = (String) row.getDoc().get("_id");
                                if (postId == null) {
                                    postId = (String) row.getDoc().get("id");
                                }
                                // Se estamos editando, ignorar se é o mesmo post
                                return excludePostId == null || !excludePostId.equals(postId);
                            });
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    int statusCode = ex.getStatusCode().value();
                    // 404 = view não existe, 400 = view não existe ou query inválida
                    if (statusCode == 404 || statusCode == 400) {
                        // View não existe ou query inválida, usar fallback
                        log.debug("View por slug não disponível (status: {}), usando fallback", statusCode);
                        return checkSlugExistsFallback(sanitizedSlug, excludePostId);
                    }
                    log.debug("Erro ao verificar slug, assumindo que não existe: {}", ex.getMessage());
                    return Mono.just(false);
                })
                .switchIfEmpty(Mono.just(false))
                .defaultIfEmpty(false);
    }
    
    /**
     * Fallback para verificar se slug existe quando view específica não está disponível.
     * @param slug O slug sanitizado
     * @param excludePostId ID do post a ser excluído
     * @return Mono<Boolean> true se existe, false caso contrário
     */
    private Mono<Boolean> checkSlugExistsFallback(String slug, String excludePostId) {
        return getPostBySlug(slug)
                .map(post -> {
                    // Se estamos editando um post, ignorar se o slug pertence ao mesmo post
                    return excludePostId == null || !excludePostId.equals(post.getId());
                })
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }
    
    /**
     * Busca um post pelo ID.
     * @param id O ID do post
     * @return Mono com o post encontrado ou vazio se não encontrado
     */
    public Mono<Post> getPostById(String id) {
        if (id == null || id.isBlank()) {
            log.warn("Tentativa de buscar post com ID vazio ou nulo");
            return Mono.error(new IllegalArgumentException("ID não pode ser vazio"));
        }
        
        String sanitizedId = sanitizeDocumentId(id);
        String uri = buildDocumentUri(sanitizedId);
        
        return couchDbWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .map(doc -> postMapper.mapDocumentToPost(doc))
                .filter(post -> post != null)
                .doOnNext(post -> log.info("Post carregado por ID: {}", post.getTitle()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    int statusCode = ex.getStatusCode().value();
                    if (statusCode == 404) {
                        log.debug("Post não encontrado com ID: {}", sanitizedId);
                        return Mono.empty();
                    }
                    log.error("Erro ao buscar post do CouchDB. Status: {}, ID: {}", 
                            ex.getStatusCode(), sanitizedId);
                    return Mono.error(new RuntimeException("Erro ao buscar post", ex));
                });
    }
    
    /**
     * Cria um novo post no CouchDB.
     * @param post O post a ser criado (deve ter ID gerado)
     * @return Mono com o post criado (incluindo _rev)
     */
    public Mono<Post> createPost(Post post) {
        if (post == null) {
            return Mono.error(new IllegalArgumentException("Post não pode ser nulo"));
        }
        
        if (post.getId() == null || post.getId().isBlank()) {
            return Mono.error(new IllegalArgumentException("ID do post não pode ser vazio"));
        }
        
        String sanitizedId = sanitizeDocumentId(post.getId());
        String uri = buildDocumentUri(sanitizedId);
        
        // Converter Post para Map para enviar ao CouchDB
        Map<String, Object> doc = postMapper.mapPostToDocument(post);
        
        return couchDbWebClient.put()
                .uri(uri)
                .bodyValue(doc)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    // Atualizar _rev no post
                    String rev = (String) response.get("rev");
                    post.setRevision(rev);
                    log.info("Post criado com sucesso: {} (rev: {})", post.getTitle(), rev);
                    return post;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    int statusCode = ex.getStatusCode().value();
                    if (statusCode == 409) {
                        log.error("Conflito ao criar post: ID já existe: {}", sanitizedId);
                        return Mono.error(new IllegalArgumentException("Post com este ID já existe"));
                    }
                    log.error("Erro ao criar post no CouchDB. Status: {}, ID: {}", 
                            ex.getStatusCode(), sanitizedId, ex);
                    return Mono.error(new RuntimeException("Erro ao criar post", ex));
                });
    }
    
    /**
     * Atualiza um post existente no CouchDB.
     * @param post O post a ser atualizado (deve ter ID e _rev)
     * @return Mono com o post atualizado (incluindo nova _rev)
     */
    public Mono<Post> updatePost(Post post) {
        if (post == null) {
            return Mono.error(new IllegalArgumentException("Post não pode ser nulo"));
        }
        
        if (post.getId() == null || post.getId().isBlank()) {
            return Mono.error(new IllegalArgumentException("ID do post não pode ser vazio"));
        }
        
        if (post.getRevision() == null || post.getRevision().isBlank()) {
            return Mono.error(new IllegalArgumentException("Revisão do post não pode ser vazia. Busque o post primeiro para obter a revisão atual."));
        }
        
        String sanitizedId = sanitizeDocumentId(post.getId());
        String uri = buildDocumentUri(sanitizedId);
        
        // Converter Post para Map para enviar ao CouchDB
        Map<String, Object> doc = postMapper.mapPostToDocument(post);
        
        return couchDbWebClient.put()
                .uri(uri)
                .bodyValue(doc)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    // Atualizar _rev no post
                    String rev = (String) response.get("rev");
                    post.setRevision(rev);
                    log.info("Post atualizado com sucesso: {} (rev: {})", post.getTitle(), rev);
                    return post;
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    int statusCode = ex.getStatusCode().value();
                    if (statusCode == 409) {
                        log.error("Conflito ao atualizar post: Revisão desatualizada. ID: {}", sanitizedId);
                        return Mono.error(new IllegalArgumentException("Post foi modificado por outro processo. Busque a versão mais recente e tente novamente."));
                    }
                    if (statusCode == 404) {
                        log.error("Post não encontrado para atualização. ID: {}", sanitizedId);
                        return Mono.error(new IllegalArgumentException("Post não encontrado"));
                    }
                    log.error("Erro ao atualizar post no CouchDB. Status: {}, ID: {}", 
                            ex.getStatusCode(), sanitizedId, ex);
                    return Mono.error(new RuntimeException("Erro ao atualizar post", ex));
                });
    }
    
    /**
     * Deleta um post do CouchDB.
     * @param id ID do post a ser deletado
     * @param revision Revisão do post (necessária para deletar no CouchDB)
     * @return Mono<Void> que completa quando o post é deletado
     */
    public Mono<Void> deletePost(String id, String revision) {
        if (id == null || id.isBlank()) {
            return Mono.error(new IllegalArgumentException("ID do post não pode ser vazio"));
        }
        
        if (revision == null || revision.isBlank()) {
            return Mono.error(new IllegalArgumentException("Revisão do post não pode ser vazia. Busque o post primeiro para obter a revisão atual."));
        }
        
        String sanitizedId = sanitizeDocumentId(id);
        String uri = buildDocumentUriWithRevision(sanitizedId, revision);
        
        return couchDbWebClient.delete()
                .uri(uri)
                .retrieve()
                .bodyToMono(Map.class)
                .then()
                .doOnSuccess(v -> log.info("Post deletado com sucesso. ID: {}", sanitizedId))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    int statusCode = ex.getStatusCode().value();
                    if (statusCode == 404) {
                        log.error("Post não encontrado para deleção. ID: {}", sanitizedId);
                        return Mono.error(new IllegalArgumentException("Post não encontrado"));
                    }
                    if (statusCode == 409) {
                        log.error("Conflito ao deletar post: Revisão desatualizada. ID: {}", sanitizedId);
                        return Mono.error(new IllegalArgumentException("Post foi modificado por outro processo. Busque a versão mais recente e tente novamente."));
                    }
                    log.error("Erro ao deletar post no CouchDB. Status: {}, ID: {}", 
                            ex.getStatusCode(), sanitizedId, ex);
                    return Mono.error(new RuntimeException("Erro ao deletar post", ex));
                });
    }
    
    /**
     * Constrói URI para um documento específico.
     * @param documentId ID do documento
     * @return URI sanitizada
     */
    private String buildDocumentUri(String documentId) {
        return "/" + sanitizeDatabaseName(databaseName) + "/" + sanitizeDocumentId(documentId);
    }
    
    /**
     * Constrói URI para um documento específico com revisão (usado para DELETE).
     * @param documentId ID do documento
     * @param revision Revisão do documento
     * @return URI sanitizada com parâmetro rev
     */
    private String buildDocumentUriWithRevision(String documentId, String revision) {
        String sanitizedRevision = sanitizeRevision(revision);
        return "/" + sanitizeDatabaseName(databaseName) + "/" + sanitizeDocumentId(documentId) 
                + "?rev=" + sanitizedRevision;
    }
    
    /**
     * Sanitiza uma revisão do CouchDB.
     * @param revision A revisão a ser sanitizada
     * @return Revisão sanitizada
     */
    private String sanitizeRevision(String revision) {
        if (revision == null || revision.isBlank()) {
            throw new IllegalArgumentException("Revisão não pode ser vazia");
        }
        // Revisões do CouchDB são números seguidos de hífen e hash
        return revision.replaceAll("[^0-9a-zA-Z-]", "");
    }
    
    /**
     * Sanitiza um ID de documento para prevenir path traversal.
     * @param docId ID do documento
     * @return ID sanitizado
     */
    private String sanitizeDocumentId(String docId) {
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("ID do documento não pode ser vazio");
        }
        // Remove caracteres perigosos, mas mantém caracteres válidos para IDs do CouchDB
        return docId.replaceAll("[^a-zA-Z0-9_$()+/-]", "");
    }
    
    // ========== Métodos auxiliares para construção de URIs ==========
    /**
     * Constrói URI para uma view com parâmetros opcionais.
     * @param viewPath Caminho da view
     * @param params Parâmetros adicionais
     * @return URI sanitizada
     */
    private String buildViewUri(String viewPath, String... params) {
        StringBuilder uri = new StringBuilder("/")
                .append(sanitizeDatabaseName(databaseName))
                .append("/")
                .append(viewPath);
        
        if (params.length > 0) {
            uri.append("?");
            uri.append(String.join("&", params));
        }
        
        return uri.toString();
    }
    
    /**
     * Constrói URI para uma view com paginação.
     * @param viewPath Caminho da view
     * @param skip Número de registros a pular
     * @param limit Limite de registros
     * @param params Parâmetros adicionais
     * @return URI sanitizada
     */
    private String buildViewUriWithPagination(String viewPath, int skip, int limit, String... params) {
        StringBuilder uri = new StringBuilder("/")
                .append(sanitizeDatabaseName(databaseName))
                .append("/")
                .append(viewPath)
                .append("?");
        
        if (params.length > 0) {
            uri.append(String.join("&", params));
            uri.append("&");
        }
        
        uri.append(PARAM_SKIP).append("=").append(skip);
        uri.append("&").append(PARAM_LIMIT).append("=").append(limit);
        
        return uri.toString();
    }
    
    /**
     * Constrói URI para uma view com key específica.
     * @param viewPath Caminho da view
     * @param key A chave a ser buscada (será URL encoded como JSON string)
     * @param params Parâmetros adicionais
     * @return URI sanitizada
     */
    private String buildViewUriWithKey(String viewPath, String key, String... params) {
        StringBuilder uri = new StringBuilder("/")
                .append(sanitizeDatabaseName(databaseName))
                .append("/")
                .append(viewPath)
                .append("?");
        
        if (params.length > 0) {
            uri.append(String.join("&", params));
            uri.append("&");
        }
        
        // CouchDB espera a key como JSON string, então precisamos codificar corretamente
        // Formato: key="valor" (JSON string)
        String jsonKey = "\"" + key.replace("\"", "\\\"") + "\"";
        String encodedKey = URLEncoder.encode(jsonKey, StandardCharsets.UTF_8)
                .replace("+", "%20") // Espaços devem ser %20, não +
                .replace("%21", "!") // Manter alguns caracteres não codificados para JSON
                .replace("%27", "'");
        
        uri.append(PARAM_KEY).append("=").append(encodedKey);
        
        return uri.toString();
    }
    
    // ========== Métodos auxiliares de sanitização ==========
    /**
     * Sanitiza o nome do banco de dados para prevenir path traversal.
     * @param dbName Nome do banco
     * @return Nome sanitizado
     */
    private String sanitizeDatabaseName(String dbName) {
        if (dbName == null || dbName.isBlank()) {
            throw new IllegalArgumentException("Nome do banco de dados não pode ser vazio");
        }
        // Remove caracteres perigosos
        return dbName.replaceAll("[^a-zA-Z0-9_$()+/-]", "");
    }
    
    /**
     * Sanitiza um slug removendo caracteres perigosos.
     * @param slug O slug a ser sanitizado
     * @return Slug sanitizado
     */
    private String sanitizeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return "";
        }
        // Remove caracteres que podem causar problemas em URLs
        return slug
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
