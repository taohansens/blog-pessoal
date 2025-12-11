package br.com.taohansen.blog.services;

import br.com.taohansen.blog.models.CreatePostRequest;
import br.com.taohansen.blog.models.PagedPostsResponse;
import br.com.taohansen.blog.models.Post;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Camada de serviço para operações de post.
 * Encapsula o CouchDbService (repositório) e centraliza regras como geração/validação de slug.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final CouchDbService couchDbService;
    private final SlugService slugService;

    public Flux<Post> listPosts(boolean includeDrafts) {
        return couchDbService.listPosts(includeDrafts);
    }

    public Mono<Post> getPostBySlug(String slug, boolean includeDrafts) {
        return couchDbService.getPostBySlug(slug, includeDrafts);
    }

    public Mono<PagedPostsResponse> listPostsPaged(int page, int size, boolean includeDrafts) {
        return couchDbService.listPostsPaged(page, size, includeDrafts);
    }

    public Mono<Post> createPost(CreatePostRequest request) {
        Mono<String> slugMono;
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            if (!slugService.isValidSlug(request.getSlug())) {
                return Mono.error(new IllegalArgumentException("Slug inválido"));
            }
            slugMono = couchDbService.slugExists(request.getSlug(), null)
                    .flatMap(exists -> {
                        if (exists) {
                            log.warn("Tentativa de criar post com slug já existente: {}", request.getSlug());
                            return Mono.error(new IllegalArgumentException("Slug já existe: " + request.getSlug()));
                        }
                        return Mono.just(request.getSlug());
                    });
        } else {
            slugMono = slugService.generateUniqueSlugFromTitle(request.getTitle(), null);
        }

        return slugMono
                .flatMap(slug -> {
                    Post post = Post.builder()
                            .id(UUID.randomUUID().toString())
                            .type("blog_post")
                            .title(request.getTitle())
                            .slug(slug)
                            .date(request.getDate() != null ? request.getDate() : LocalDateTime.now())
                            .tags(request.getTags() != null ? request.getTags() : new java.util.ArrayList<>())
                            .summary(request.getSummary())
                            .content(request.getContent())
                            .draft(request.getDraft() != null ? request.getDraft() : false)
                            .image(request.getImage())
                            .build();

                    return couchDbService.createPost(post);
                });
    }

    public Mono<Post> updatePost(String id, CreatePostRequest request) {
        return couchDbService.getPostById(id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Post não encontrado para atualização: {}", id);
                    return Mono.error(new IllegalArgumentException("Post não encontrado"));
                }))
                .flatMap(existingPost -> {
                    Mono<String> slugMono;
                    if (request.getSlug() != null && !request.getSlug().isBlank()) {
                        if (!slugService.isValidSlug(request.getSlug())) {
                            return Mono.error(new IllegalArgumentException("Slug inválido"));
                        }
                        slugMono = couchDbService.slugExists(request.getSlug(), id)
                                .flatMap(exists -> {
                                    if (exists) {
                                        log.warn("Tentativa de atualizar post com slug já existente: {}", request.getSlug());
                                        return Mono.error(new IllegalArgumentException("Slug já existe: " + request.getSlug()));
                                    }
                                    return Mono.just(request.getSlug());
                                });
                    } else if (request.getTitle() != null && !request.getTitle().equals(existingPost.getTitle())) {
                        slugMono = slugService.generateUniqueSlugFromTitle(request.getTitle(), id);
                    } else {
                        slugMono = Mono.just(existingPost.getSlug());
                    }

                    return slugMono.flatMap(slug -> {
                        existingPost.setTitle(request.getTitle());
                        existingPost.setSlug(slug);
                        if (request.getDate() != null) {
                            existingPost.setDate(request.getDate());
                        }
                        existingPost.setTags(request.getTags() != null ? request.getTags() : new java.util.ArrayList<>());
                        existingPost.setSummary(request.getSummary());
                        existingPost.setContent(request.getContent());
                        if (request.getDraft() != null) {
                            existingPost.setDraft(request.getDraft());
                        }
                        existingPost.setImage(request.getImage());
                        existingPost.setUpdatedAt(LocalDateTime.now());

                        return couchDbService.updatePost(existingPost);
                    });
                });
    }

    public Mono<Void> deletePost(String id) {
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
                });
    }
}
