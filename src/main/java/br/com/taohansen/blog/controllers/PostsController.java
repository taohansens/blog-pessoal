package br.com.taohansen.blog.controllers;

import br.com.taohansen.blog.dto.post.PagedPostResponse;
import br.com.taohansen.blog.dto.post.PostResponse;
import br.com.taohansen.blog.mappers.PostMapper;
import br.com.taohansen.blog.services.PostService;
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

/**
 * Controller REST responsável por disponibilizar os endpoints públicos de leitura
 * de posts do blog.
 * <p>
 * Todos os endpoints são reativos (WebFlux), retornando {@link Mono} para
 * permitir processamento assíncrono e não bloqueante.
 * </p>
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PostsController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MIN_PAGE_SIZE = 1;
    private static final String SLUG_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private final PostService postService;
    private final PostMapper postMapper;

    /**
     * Lista posts de forma paginada.
     *
     * @param page número da página (zero-based, mínimo {@code 0}); caso não informado,
     *             assume o valor padrão definido em {@code @RequestParam(defaultValue = "0")}
     * @param size quantidade de registros por página (entre {@value MIN_PAGE_SIZE} e
     *             {@value MAX_PAGE_SIZE}); caso não informado, assume o valor padrão
     *             definido em {@code @RequestParam(defaultValue = "10")}
     * @return {@link Mono} contendo um {@link ResponseEntity} com {@link PagedPostResponse}
     * em caso de sucesso, {@code 404 (Not Found)} quando não houver registros para o
     * critério informado ou {@code 500 (Internal Server Error)} em caso de erro interno
     */
    @GetMapping
    public Mono<ResponseEntity<PagedPostResponse>> getAllPaged(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Página deve ser >= 0")
            int page,

            @RequestParam(defaultValue = "10")
            @Min(value = MIN_PAGE_SIZE, message = "Tamanho da página deve ser >= 1")
            @Max(value = MAX_PAGE_SIZE, message = "Tamanho da página deve ser <= 50")
            int size) {

        log.debug("Listando posts paginados - página: {}, tamanho: {}", page, size);

        return postService.listPostsPaged(page, size, false)
                .map(postMapper::toPagedResponse)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(ex -> {
                    log.error("Erro ao listar posts paginados - página: {}, tamanho: {}", page, size, ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }

    /**
     * Busca um post específico pelo seu slug.
     *
     * @param slug identificador amigável do post, validado contra o padrão
     *             {@link #SLUG_PATTERN}; não pode ser nulo nem vazio
     * @return {@link Mono} contendo um {@link ResponseEntity} com {@link PostResponse}
     * quando o post é encontrado, {@code 404 (Not Found)} caso não exista post para o
     * slug informado ou {@code 500 (Internal Server Error)} em caso de erro interno
     */
    @GetMapping("/{slug}")
    public Mono<ResponseEntity<PostResponse>> getPost(
            @PathVariable
            @NotBlank(message = "Slug não pode ser vazio")
            @Pattern(regexp = SLUG_PATTERN, message = "Slug inválido")
            String slug) {
        
        return postService.getPostBySlug(slug, false)
                .flatMap(post -> Mono.justOrEmpty(postMapper.toResponse(post)))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(ex -> {
                    log.debug("Erro ao buscar post com slug: {}", slug, ex);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }
}
