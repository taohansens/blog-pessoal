package br.com.taohansen.blog.services;

import br.com.taohansen.blog.models.CreatePostRequest;
import br.com.taohansen.blog.models.PagedPost;
import br.com.taohansen.blog.models.Post;
import br.com.taohansen.blog.repository.CouchDbRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Camada de serviço para operações de {@link Post}.
 * Encapsula o {@link CouchDbRepository} (repositório) e centraliza regras como geração e validação de slug.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final CouchDbRepository couchDbRepository;
    private final SlugService slugService;

    /**
     * Lista posts ordenados por data, delegando a lógica de filtro de rascunho para o {@link CouchDbRepository}.
     *
     * @param includeDrafts se {@code true}, inclui rascunhos (uso típico em área/admin)
     * @return {@link Flux} de {@link Post}
     */
    public Flux<Post> listPosts(boolean includeDrafts) {
        return couchDbRepository.listPosts(includeDrafts);
    }

    /**
     * Busca um post pelo slug.
     *
     * @param slug          slug do post
     * @param includeDrafts se {@code true}, inclui rascunhos (uso típico em área/admin)
     * @return {@link Mono} com o post, vazio se não encontrado
     */
    public Mono<Post> getPostBySlug(String slug, boolean includeDrafts) {
        return couchDbRepository.getPostBySlug(slug, includeDrafts);
    }

    /**
     * Lista posts paginados.
     *
     * @param page          página (0-indexed)
     * @param size          tamanho da página
     * @param includeDrafts se {@code true}, inclui rascunhos (uso típico em área/admin)
     * @return {@link Mono} com {@link PagedPost}
     */
    public Mono<PagedPost> listPostsPaged(int page, int size, boolean includeDrafts) {
        return couchDbRepository.listPostsPaged(page, size, includeDrafts);
    }

    /**
     * Cria um novo post a partir dos dados de requisição.
     * Resolve o slug (fornecido ou gerado) e delega a persistência ao repositório.
     *
     * @param request dados para criação do post
     * @return {@link Mono} com o post criado
     */
    public Mono<Post> createPost(CreatePostRequest request) {
        return resolveSlugForCreate(request)
                .flatMap(slug -> {
                    Post newPost = buildNewPostFromRequest(request, slug);
                    return couchDbRepository.createPost(newPost);
                });
    }

    /**
     * Atualiza um post existente.
     * Recalcula o slug quando necessário e aplica os campos vindos do request.
     *
     * @param id      identificador do post a ser atualizado
     * @param request dados para atualização do post
     * @return {@link Mono} com o post atualizado
     */
    public Mono<Post> updatePost(String id, CreatePostRequest request) {
        return couchDbRepository.getPostById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Post não encontrado para atualização: {}", id);
                    return Mono.error(new IllegalArgumentException("Post não encontrado"));
                }))
                .flatMap(existingPost ->
                        resolveSlugForUpdate(request, existingPost, id)
                                .flatMap(slug -> {
                                    applyRequestUpdatesToPost(existingPost, request, slug);
                                    return couchDbRepository.updatePost(existingPost);
                                })
                );
    }

    /**
     * Exclui um post existente, garantindo que uma revisão válida esteja presente.
     *
     * @param id identificador do post a ser deletado
     * @return {@link Mono} vazio que completa quando a deleção é concluída
     */
    public Mono<Void> deletePost(String id) {
        return couchDbRepository.getPostById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Post não encontrado para deleção: {}", id);
                    return Mono.error(new IllegalArgumentException("Post não encontrado"));
                }))
                .flatMap(post -> {
                    if (post.getRevision() == null || post.getRevision().isBlank()) {
                        log.error("Post sem revisão não pode ser deletado. ID: {}", id);
                        return Mono.error(new IllegalArgumentException("Post não possui revisão válida"));
                    }

                    return couchDbRepository.deletePost(post.getId(), post.getRevision());
                });
    }

    /**
     * Resolve o slug para criação de post, aplicando validação
     * e garantindo unicidade (quando o slug é informado manualmente).
     *
     * @param request requisição de criação contendo título/slug
     * @return {@link Mono} com o slug resolvido
     */
    private Mono<String> resolveSlugForCreate(CreatePostRequest request) {
        String requestedSlug = request.getSlug();

        if (requestedSlug != null && !requestedSlug.isBlank()) {
            if (!slugService.isValidSlug(requestedSlug)) {
                return Mono.error(new IllegalArgumentException("Slug inválido"));
            }
            return couchDbRepository.slugExists(requestedSlug, null)
                    .flatMap(exists -> {
                        if (exists) {
                            log.warn("Tentativa de criar post com slug já existente: {}", requestedSlug);
                            return Mono.error(new IllegalArgumentException("Slug já existe: " + requestedSlug));
                        }
                        return Mono.just(requestedSlug);
                    });
        }

        return slugService.generateUniqueSlugFromTitle(request.getTitle(), null);
    }

    /**
     * Resolve o slug para atualização de post, reaproveitando o slug atual
     * quando possível e evitando duplicidade.
     *
     * @param request     requisição de atualização contendo possíveis mudanças de título/slug
     * @param existingPost post já existente carregado do banco
     * @param postId      identificador do post (para exclusão em verificações de unicidade)
     * @return {@link Mono} com o slug resolvido
     */
    private Mono<String> resolveSlugForUpdate(CreatePostRequest request, Post existingPost, String postId) {
        String requestedSlug = request.getSlug();

        if (requestedSlug != null && !requestedSlug.isBlank()) {
            if (!slugService.isValidSlug(requestedSlug)) {
                return Mono.error(new IllegalArgumentException("Slug inválido"));
            }
            return couchDbRepository.slugExists(requestedSlug, postId)
                    .flatMap(exists -> {
                        if (exists) {
                            log.warn("Tentativa de atualizar post com slug já existente: {}", requestedSlug);
                            return Mono.error(new IllegalArgumentException("Slug já existe: " + requestedSlug));
                        }
                        return Mono.just(requestedSlug);
                    });
        }

        if (request.getTitle() != null && !request.getTitle().equals(existingPost.getTitle())) {
            return slugService.generateUniqueSlugFromTitle(request.getTitle(), postId);
        }

        return Mono.just(existingPost.getSlug());
    }

    /**
     * Constrói um novo {@link Post} a partir do request e do slug já resolvido.
     *
     * @param request dados de criação do post
     * @param slug    slug já validado e resolvido
     * @return instância de {@link Post} pronta para persistência
     */
    private Post buildNewPostFromRequest(CreatePostRequest request, String slug) {
        return Post.builder()
                .id(UUID.randomUUID().toString())
                .type("blog_post")
                .title(request.getTitle())
                .slug(slug)
                .date(request.getDate() != null ? request.getDate() : LocalDateTime.now())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .summary(request.getSummary())
                .content(request.getContent())
                .draft(request.getDraft() != null ? request.getDraft() : false)
                .image(request.getImage())
                .build();
    }

    /**
     * Aplica as alterações do request em um post existente,
     * utilizando o slug já resolvido.
     *
     * @param existingPost post carregado do banco que será modificado
     * @param request      dados de atualização
     * @param slug         slug já validado e resolvido
     */
    private void applyRequestUpdatesToPost(Post existingPost, CreatePostRequest request, String slug) {
        existingPost.setTitle(request.getTitle());
        existingPost.setSlug(slug);

        if (request.getDate() != null) {
            existingPost.setDate(request.getDate());
        }

        existingPost.setTags(request.getTags() != null ? request.getTags() : new ArrayList<>());
        existingPost.setSummary(request.getSummary());
        existingPost.setContent(request.getContent());

        if (request.getDraft() != null) {
            existingPost.setDraft(request.getDraft());
        }

        existingPost.setImage(request.getImage());
        existingPost.setUpdatedAt(LocalDateTime.now());
    }
}
