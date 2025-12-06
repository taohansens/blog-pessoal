package br.com.taohansen.blog.controllers;

import br.com.taohansen.blog.models.CreatePostRequest;
import br.com.taohansen.blog.models.PagedPostsResponse;
import br.com.taohansen.blog.models.Post;
import br.com.taohansen.blog.services.CouchDbService;
import br.com.taohansen.blog.services.SlugService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Controller REST para gerenciamento de posts do blog.
 * 
 * Endpoints disponíveis:
 * - GET /api/posts/all - Lista todos os posts
 * - GET /api/posts/{slug} - Busca post por slug
 * - GET /api/posts?page={page}&size={size} - Lista posts paginados
 * - POST /api/posts - Cria um novo post
 * - PUT /api/posts/{id} - Atualiza um post existente
 * - DELETE /api/posts/{id} - Deleta um post existente
 * 
 * Todos os endpoints retornam JSON e seguem padrões RESTful.
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PostsController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final String SLUG_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private final CouchDbService couchDbService;
    private final SlugService slugService;

    /**
     * Lista todos os posts ordenados por data (mais recentes primeiro).
     * 
     * @return Lista completa de posts
     */
    @GetMapping("/all")
    public Mono<ResponseEntity<List<Post>>> listPosts() {
        log.debug("Listando todos os posts");
        
        return couchDbService.listPosts()
                .collectList()
                .map(posts -> {
                    log.info("Retornando {} posts", posts.size());
                    return ResponseEntity.ok(posts);
                })
                .onErrorResume(ex -> {
                    log.error("Erro ao listar posts", ex);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }

    /**
     * Busca um post específico pelo slug.
     * 
     * @param slug Slug do post (validado)
     * @return Post encontrado ou 404 se não existir
     */
    @GetMapping("/{slug}")
    public Mono<ResponseEntity<Post>> getPost(
            @PathVariable 
            @NotBlank(message = "Slug não pode ser vazio")
            @Pattern(regexp = SLUG_PATTERN, message = "Slug inválido")
            String slug) {
        
        log.debug("Buscando post com slug: {}", slug);
        
        return couchDbService.getPostBySlug(slug)
                .map(post -> {
                    log.info("Post encontrado: {}", post.getTitle());
                    return ResponseEntity.ok(post);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(ex -> {
                    log.error("Erro ao buscar post com slug: {}", slug, ex);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }

    /**
     * Lista posts de forma paginada.
     * 
     * @param page Número da página (0-indexed, mínimo 0)
     * @param size Tamanho da página (entre 1 e 50)
     * @return Resposta paginada com posts
     */
    @GetMapping
    public Mono<ResponseEntity<PagedPostsResponse>> listPostsPaged(
            @RequestParam(defaultValue = "0") 
            @Min(value = 0, message = "Página deve ser >= 0")
            int page,
            
            @RequestParam(defaultValue = "10") 
            @Min(value = MIN_PAGE_SIZE, message = "Tamanho da página deve ser >= 1")
            @Max(value = MAX_PAGE_SIZE, message = "Tamanho da página deve ser <= 50")
            int size) {

        log.debug("Listando posts paginados - página: {}, tamanho: {}", page, size);

        return couchDbService.listPostsPaged(page, size)
                .map(paged -> {
                    log.info("Retornando página {} com {} posts (total: {})", 
                            page, paged.getPosts().size(), paged.getTotal());
                    return ResponseEntity.ok(paged);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(ex -> {
                    log.error("Erro ao listar posts paginados - página: {}, tamanho: {}", 
                            page, size, ex);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }

    /**
     * Cria um novo post.
     * 
     * @param request Dados do post a ser criado
     * @return Post criado com ID e revisão
     */
    @PostMapping
    public Mono<ResponseEntity<Post>> createPost(
            @RequestBody @org.springframework.validation.annotation.Validated CreatePostRequest request) {
        
        log.debug("Criando novo post: {}", request.getTitle());
        
        // Gerar slug se não fornecido
        Mono<String> slugMono;
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            // Validar slug fornecido
            if (!slugService.isValidSlug(request.getSlug())) {
                return Mono.just(ResponseEntity
                        .badRequest()
                        .build());
            }
            // Verificar se slug já existe
            slugMono = couchDbService.slugExists(request.getSlug(), null)
                    .flatMap(exists -> {
                        if (exists) {
                            log.warn("Tentativa de criar post com slug já existente: {}", request.getSlug());
                            return Mono.error(new IllegalArgumentException("Slug já existe: " + request.getSlug()));
                        }
                        return Mono.just(request.getSlug());
                    });
        } else {
            // Gerar slug automaticamente a partir do título
            slugMono = slugService.generateUniqueSlugFromTitle(request.getTitle(), null);
        }
        
        return slugMono
                .flatMap(slug -> {
                    // Criar objeto Post
                    Post post = Post.builder()
                            .id(UUID.randomUUID().toString())
                            .type("blog_post")
                            .title(request.getTitle())
                            .slug(slug)
                            .date(request.getDate() != null ? request.getDate() : LocalDate.now())
                            .tags(request.getTags() != null ? request.getTags() : new java.util.ArrayList<>())
                            .summary(request.getSummary())
                            .content(request.getContent())
                            .build();
                    
                    return couchDbService.createPost(post);
                })
                .map(post -> {
                    log.info("Post criado com sucesso: {} (ID: {})", post.getTitle(), post.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(post);
                })
                .onErrorResume(IllegalArgumentException.class, ex -> {
                    log.warn("Erro de validação ao criar post: {}", ex.getMessage());
                    return Mono.just(ResponseEntity
                            .badRequest()
                            .build());
                })
                .onErrorResume(ex -> {
                    log.error("Erro ao criar post", ex);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }

    /**
     * Atualiza um post existente.
     * 
     * @param id ID do post a ser atualizado
     * @param request Dados atualizados do post
     * @return Post atualizado
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Post>> updatePost(
            @PathVariable 
            @NotBlank(message = "ID não pode ser vazio")
            String id,
            @RequestBody @org.springframework.validation.annotation.Validated CreatePostRequest request) {
        
        log.debug("Atualizando post com ID: {}", id);
        
        // Buscar post existente para obter _rev
        return couchDbService.getPostById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Post não encontrado para atualização: {}", id);
                    return Mono.error(new IllegalArgumentException("Post não encontrado"));
                }))
                .flatMap(existingPost -> {
                    // Gerar slug se não fornecido ou se mudou
                    Mono<String> slugMono;
                    if (request.getSlug() != null && !request.getSlug().isBlank()) {
                        // Validar slug fornecido
                        if (!slugService.isValidSlug(request.getSlug())) {
                            return Mono.error(new IllegalArgumentException("Slug inválido"));
                        }
                        // Verificar se slug já existe (excluindo o post atual)
                        slugMono = couchDbService.slugExists(request.getSlug(), id)
                                .flatMap(exists -> {
                                    if (exists) {
                                        log.warn("Tentativa de atualizar post com slug já existente: {}", request.getSlug());
                                        return Mono.error(new IllegalArgumentException("Slug já existe: " + request.getSlug()));
                                    }
                                    return Mono.just(request.getSlug());
                                });
                    } else if (request.getTitle() != null && !request.getTitle().equals(existingPost.getTitle())) {
                        // Título mudou, gerar novo slug
                        slugMono = slugService.generateUniqueSlugFromTitle(request.getTitle(), id);
                    } else {
                        // Manter slug existente
                        slugMono = Mono.just(existingPost.getSlug());
                    }
                    
                    return slugMono.flatMap(slug -> {
                        // Atualizar campos do post
                        existingPost.setTitle(request.getTitle());
                        existingPost.setSlug(slug);
                        if (request.getDate() != null) {
                            existingPost.setDate(request.getDate());
                        }
                        existingPost.setTags(request.getTags() != null ? request.getTags() : new java.util.ArrayList<>());
                        existingPost.setSummary(request.getSummary());
                        existingPost.setContent(request.getContent());
                        
                        return couchDbService.updatePost(existingPost);
                    });
                })
                .map(post -> {
                    log.info("Post atualizado com sucesso: {} (ID: {})", post.getTitle(), post.getId());
                    return ResponseEntity.ok(post);
                })
                .onErrorResume(IllegalArgumentException.class, ex -> {
                    log.warn("Erro de validação ao atualizar post: {}", ex.getMessage());
                    return Mono.just(ResponseEntity
                            .badRequest()
                            .build());
                })
                .onErrorResume(ex -> {
                    log.error("Erro ao atualizar post", ex);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }

    /**
     * Deleta um post existente.
     * 
     * @param id ID do post a ser deletado
     * @return Resposta vazia (204 No Content) em caso de sucesso
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deletePost(
            @PathVariable 
            @NotBlank(message = "ID não pode ser vazio")
            String id) {
        
        log.debug("Deletando post com ID: {}", id);
        
        // Buscar post existente para obter _rev
        return couchDbService.getPostById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Post não encontrado para deleção: {}", id);
                    return Mono.error(new IllegalArgumentException("Post não encontrado"));
                }))
                .flatMap(post -> {
                    if (post.getRevision() == null || post.getRevision().isBlank()) {
                        log.error("Post sem revisão não pode ser deletado. ID: {}", id);
                        return Mono.error(new IllegalArgumentException("Post não possui revisão válida"));
                    }
                    
                    return couchDbService.deletePost(post.getId(), post.getRevision());
                })
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(IllegalArgumentException.class, ex -> {
                    log.warn("Erro de validação ao deletar post: {}", ex.getMessage());
                    return Mono.just(ResponseEntity
                            .badRequest()
                            .build());
                })
                .onErrorResume(ex -> {
                    log.error("Erro ao deletar post", ex);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }
}
