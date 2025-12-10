package br.com.taohansen.blog.controllers;

import br.com.taohansen.blog.models.MediaResource;
import br.com.taohansen.blog.security.AdminService;
import br.com.taohansen.blog.security.JwtService;
import br.com.taohansen.blog.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/admin/media")
@Validated
@RequiredArgsConstructor
@Slf4j
public class AdminMediaController {

    private final CloudinaryService cloudinaryService;
    private final AdminService adminService;
    private final JwtService jwtService;
    private static final String BEARER_PREFIX = "Bearer ";

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<MediaResource>> uploadImage(@RequestPart("file") FilePart file,
                                                           ServerWebExchange exchange) {
        return isAdminUser(exchange)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }
                    return cloudinaryService.uploadImage(file)
                            .map(resource -> ResponseEntity.status(HttpStatus.CREATED).body(resource))
                            .onErrorResume(ex -> {
                                log.error("Erro ao fazer upload da imagem", ex);
                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                            });
                });
    }

    @GetMapping
    public Mono<ResponseEntity<List<MediaResource>>> listImages(
            @RequestParam(value = "max", required = false) Integer max,
            ServerWebExchange exchange) {
        return isAdminUser(exchange)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }
                    return cloudinaryService.listImages(max)
                            .map(resources -> ResponseEntity.ok(resources))
                            .onErrorResume(ex -> {
                                log.error("Erro ao listar imagens", ex);
                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                            });
                });
    }

    @DeleteMapping("/{publicId}")
    public Mono<ResponseEntity<Void>> deleteImage(@PathVariable String publicId,
                                                  ServerWebExchange exchange) {
        return isAdminUser(exchange)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    }
                    return cloudinaryService.deleteImage(publicId)
                            .thenReturn(ResponseEntity.noContent().<Void>build())
                            .onErrorResume(ex -> {
                                log.error("Erro ao deletar imagem {}", publicId, ex);
                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                            });
                });
    }

    private Mono<Boolean> isAdminUser(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> {
                    if (authentication == null || !authentication.isAuthenticated()) {
                        return Mono.just(false);
                    }

                    boolean isAdmin = false;
                    String userIdentifier = null;

                    if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                        OAuth2User oauth2User = oauth2Token.getPrincipal();
                        if (oauth2User != null) {
                            userIdentifier = getEmailFromOAuth2User(oauth2User);
                            isAdmin = adminService.isAdmin(userIdentifier);
                        }
                    } else if (authentication instanceof UsernamePasswordAuthenticationToken) {
                        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                            String token = authHeader.substring(BEARER_PREFIX.length());
                            if (jwtService.validateToken(token)) {
                                userIdentifier = jwtService.extractEmail(token);
                                if (userIdentifier == null) {
                                    userIdentifier = jwtService.extractLogin(token);
                                }
                                isAdmin = Boolean.TRUE.equals(jwtService.extractIsAdmin(token));
                            }
                        }
                    }

                    log.debug("Verificação de admin (media) - usuário: {}, é admin: {}", userIdentifier, isAdmin);
                    return Mono.just(isAdmin);
                })
                .defaultIfEmpty(false);
    }

    private String getEmailFromOAuth2User(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        if (email == null || email.isBlank()) {
            email = oauth2User.getAttribute("login");
        }
        return email;
    }
}

