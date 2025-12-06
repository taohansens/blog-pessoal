package br.com.taohansen.blog.controllers;

import br.com.taohansen.blog.models.PagedPostsResponse;
import br.com.taohansen.blog.models.Post;
import br.com.taohansen.blog.services.CouchDbService;
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

import java.util.List;

/**
 * Controller REST para gerenciamento de posts do blog.
 * 
 * Endpoints disponíveis:
 * - GET /api/posts/all - Lista todos os posts
 * - GET /api/posts/{slug} - Busca post por slug
 * - GET /api/posts?page={page}&size={size} - Lista posts paginados
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
}
