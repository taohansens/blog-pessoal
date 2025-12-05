package br.com.taohansen.blog.controllers;

import br.com.taohansen.blog.models.PagedPostsResponse;
import br.com.taohansen.blog.models.Post;
import br.com.taohansen.blog.services.CouchDbService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostsController {

    private static final int MAX_PAGE_SIZE = 50;

    private final CouchDbService couchDbService;

    @GetMapping("/all")
    public Mono<ResponseEntity<List<Post>>> listPosts() {
        return couchDbService.listPosts()
                .collectList()
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    @GetMapping("/{slug}")
    public Mono<ResponseEntity<Post>> getPost(@PathVariable String slug) {
        if (slug == null || slug.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        
        return couchDbService.getPostBySlug(slug)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }

    @GetMapping
    public Mono<ResponseEntity<PagedPostsResponse>> listPostsPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        // Validações
        if (page < 0) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        
        if (size <= 0 || size > MAX_PAGE_SIZE) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return couchDbService.listPostsPaged(page, size)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
    }
}
