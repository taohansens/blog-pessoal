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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
        String docId = "post-" + slug;
        String uri = String.format("/%s/%s", databaseName, docId);
        
        return couchDbWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(Post.class)
                .doOnNext(post -> log.info("Post carregado: {}", post.getTitle()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                        log.debug("Post não encontrado: {}", slug);
                        return Mono.empty();
                    }
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
}
