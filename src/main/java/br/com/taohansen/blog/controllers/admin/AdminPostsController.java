package br.com.taohansen.blog.controllers.admin;

import br.com.taohansen.blog.dto.post.PagedPostResponse;
import br.com.taohansen.blog.dto.post.PostResponse;
import br.com.taohansen.blog.models.CreatePostRequest;
import br.com.taohansen.blog.mappers.PostMapper;
import br.com.taohansen.blog.security.AdminAuthorizationService;
import br.com.taohansen.blog.services.PostService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Endpoints administrativos para gerenciamento de posts.
 * Requer usuário admin autenticado (JWT ou OAuth2).
 */
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AdminPostsController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MIN_PAGE_SIZE = 1;
    private static final String SLUG_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private final PostService postService;
    private final PostMapper postMapper;
    private final AdminAuthorizationService adminAuthorizationService;

    /**
     * Lista todos os posts (incluindo rascunhos) para uso em área administrativa.
     *
     * @param exchange contexto da requisição (usado para extrair informações de autenticação)
     * @return {@link Mono} com {@link ResponseEntity} contendo a lista de posts ou erro apropriado
     */
    @GetMapping("/all")
    public Mono<ResponseEntity<List<PostResponse>>> listPosts(ServerWebExchange exchange) {
        log.debug("Listando todos os posts (admin)");

        return isAdminUser(exchange)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }

                    return postService.listPosts(true)
                            .map(postMapper::toResponse)
                            .collectList()
                            .map(posts -> {
                                log.info("Retornando {} posts (admin, incluindo rascunhos)", posts.size());
                                return ResponseEntity.ok(posts);
                            })
                            .onErrorResume(ex -> {
                                log.error("Erro ao listar posts (admin)", ex);
                                return Mono.just(ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .build());
                            });
                });
    }

    /**
     * Busca um post específico (incluindo rascunhos) a partir do slug.
     *
     * @param slug     identificador amigável do post
     * @param exchange contexto da requisição (usado para validação de admin)
     * @return {@link Mono} com {@link ResponseEntity} contendo o post ou código HTTP adequado
     */
    @GetMapping("/{slug}")
    public Mono<ResponseEntity<PostResponse>> getPost(
            @PathVariable
            @NotBlank(message = "Slug não pode ser vazio")
            @Pattern(regexp = SLUG_PATTERN, message = "Slug inválido")
            String slug,
            ServerWebExchange exchange) {

        log.debug("Buscando post (admin) com slug: {}", slug);

        return isAdminUser(exchange)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }

                    return postService.getPostBySlug(slug, true)
                            .map(postMapper::toResponse)
                            .map(ResponseEntity::ok)
                            .defaultIfEmpty(ResponseEntity.notFound().build())
                            .onErrorResume(ex -> {
                                log.error("Erro ao buscar post (admin) com slug: {}", slug, ex);
                                return Mono.just(ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .build());
                            });
                });
    }

    /**
     * Lista posts paginados (incluindo rascunhos) para o painel admin.
     *
     * @param page     número da página (0-indexed)
     * @param size     tamanho da página
     * @param exchange contexto da requisição (usado para validação de admin)
     * @return {@link Mono} com {@link ResponseEntity} contendo página de posts ou erro
     */
    @GetMapping
    public Mono<ResponseEntity<PagedPostResponse>> listPostsPaged(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Página deve ser >= 0")
            int page,

            @RequestParam(defaultValue = "10")
            @Min(value = MIN_PAGE_SIZE, message = "Tamanho da página deve ser >= 1")
            @Max(value = MAX_PAGE_SIZE, message = "Tamanho da página deve ser <= 50")
            int size,
            ServerWebExchange exchange) {

        log.debug("Listando posts paginados (admin) - página: {}, tamanho: {}", page, size);

        return isAdminUser(exchange)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }

                    return postService.listPostsPaged(page, size, true)
                            .map(postMapper::toPagedResponse)
                            .map(ResponseEntity::ok)
                            .defaultIfEmpty(ResponseEntity.notFound().build())
                            .onErrorResume(ex -> {
                                log.error("Erro ao listar posts paginados (admin) - página: {}, tamanho: {}",
                                        page, size, ex);
                                return Mono.just(ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .build());
                            });
                });
    }

    /**
     * Cria um novo post em contexto administrativo.
     *
     * @param request  dados para criação do post
     * @param exchange contexto da requisição (usado para validação de admin)
     * @return {@link Mono} com {@link ResponseEntity} contendo o post criado ou erro de validação/servidor
     */
    @PostMapping
    public Mono<ResponseEntity<PostResponse>> createPost(
            @RequestBody @Validated CreatePostRequest request,
            ServerWebExchange exchange) {

        log.debug("Criando novo post (admin): {}", request.getTitle());

        return isAdminUser(exchange)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }

                    return postService.createPost(request)
                            .map(postMapper::toResponse)
                            .map(post -> ResponseEntity.status(HttpStatus.CREATED).body(post))
                            .onErrorResume(IllegalArgumentException.class, ex -> {
                                log.warn("Erro de validação ao criar post (admin): {}", ex.getMessage());
                                return Mono.just(ResponseEntity
                                        .badRequest()
                                        .build());
                            })
                            .onErrorResume(ex -> {
                                log.error("Erro ao criar post (admin)", ex);
                                return Mono.just(ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .build());
                            });
                });
    }

    /**
     * Atualiza um post existente em contexto administrativo.
     *
     * @param id       identificador do post a ser atualizado
     * @param request  dados para atualização
     * @param exchange contexto da requisição (usado para validação de admin)
     * @return {@link Mono} com {@link ResponseEntity} contendo o post atualizado ou erro
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<PostResponse>> updatePost(
            @PathVariable
            @NotBlank(message = "ID não pode ser vazio")
            String id,
            @RequestBody @Validated CreatePostRequest request,
            ServerWebExchange exchange) {

        log.debug("Atualizando post (admin) com ID: {}", id);

        return isAdminUser(exchange)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }

                    return postService.updatePost(id, request)
                            .map(postMapper::toResponse)
                            .map(ResponseEntity::ok)
                            .onErrorResume(IllegalArgumentException.class, ex -> {
                                log.warn("Erro de validação ao atualizar post (admin): {}", ex.getMessage());
                                return Mono.just(ResponseEntity
                                        .badRequest()
                                        .build());
                            })
                            .onErrorResume(ex -> {
                                log.error("Erro ao atualizar post (admin)", ex);
                                return Mono.just(ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .build());
                            });
                });
    }

    /**
     * Remove um post existente em contexto administrativo.
     *
     * @param id       identificador do post a ser deletado
     * @param exchange contexto da requisição (usado para validação de admin)
     * @return {@link Mono} com {@link ResponseEntity} vazio com status adequado
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deletePost(
            @PathVariable
            @NotBlank(message = "ID não pode ser vazio")
            String id,
            ServerWebExchange exchange) {

        log.debug("Deletando post (admin) com ID: {}", id);

        return isAdminUser(exchange)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }

                    return postService.deletePost(id)
                            .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                            .onErrorResume(IllegalArgumentException.class, ex -> {
                                log.warn("Erro de validação ao deletar post (admin): {}", ex.getMessage());
                                return Mono.just(ResponseEntity
                                        .badRequest()
                                        .build());
                            })
                            .onErrorResume(ex -> {
                                log.error("Erro ao deletar post (admin)", ex);
                                return Mono.just(ResponseEntity
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .build());
                            });
                });
    }

    /**
     * Verifica se o usuário autenticado no contexto atual possui permissões de administrador.
     * Considera tanto autenticação OAuth2 quanto JWT.
     *
     * @param exchange contexto da requisição (utilizado para ler o cabeçalho Authorization em caso de JWT)
     * @return {@link Mono} com {@code true} se o usuário for admin, {@code false} caso contrário
     */
    private Mono<Boolean> isAdminUser(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> {
                    AdminAuthorizationService.AdminAuthResult result =
                            adminAuthorizationService.evaluate(exchange, authentication);
                    log.debug("Verificação de admin (posts) - usuário: {}, é admin: {}",
                            result.userIdentifier(), result.admin());
                    return result.admin();
                })
                .defaultIfEmpty(false);
    }
}
