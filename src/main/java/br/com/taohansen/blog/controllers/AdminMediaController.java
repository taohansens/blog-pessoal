package br.com.taohansen.blog.controllers;

import br.com.taohansen.blog.models.MediaResource;
import br.com.taohansen.blog.security.AdminAuthorizationService;
import br.com.taohansen.blog.services.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Endpoints administrativos para gerenciamento de mídias (imagens) do blog.
 * Requer usuário administrador autenticado para todas as operações.
 */
@RestController
@RequestMapping("/api/admin/media")
@Validated
@RequiredArgsConstructor
@Slf4j
public class AdminMediaController {

    private final CloudinaryService cloudinaryService;
    private final AdminAuthorizationService adminAuthorizationService;

    /**
     * Faz upload de uma imagem para o provedor de mídia (Cloudinary) em contexto administrativo.
     *
     * @param file     arquivo de imagem enviado em multipart
     * @param exchange contexto da requisição (usado para validação de admin)
     * @return {@link Mono} com {@link ResponseEntity} contendo os dados da mídia criada ou erro apropriado
     */
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

    /**
     * Lista imagens cadastradas no provedor de mídia.
     *
     * @param max      quantidade máxima de itens a retornar (pode ser {@code null})
     * @param exchange contexto da requisição (usado para validação de admin)
     * @return {@link Mono} com {@link ResponseEntity} contendo a lista de mídias ou erro
     */
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
                            .map(ResponseEntity::ok)
                            .onErrorResume(ex -> {
                                log.error("Erro ao listar imagens", ex);
                                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                            });
                });
    }

    /**
     * Remove uma imagem do provedor de mídia a partir de seu identificador público.
     *
     * @param publicId identificador público da imagem no provedor
     * @param exchange contexto da requisição (usado para validação de admin)
     * @return {@link Mono} com {@link ResponseEntity} vazio com status adequado
     */
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

    /**
     * Verifica, de forma reativa, se o usuário atual possui privilégios de administrador.
     *
     * @param exchange contexto da requisição (necessário para avaliação baseada em JWT)
     * @return {@link Mono} com {@code true} se for admin, {@code false} caso contrário
     */
    private Mono<Boolean> isAdminUser(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(authentication -> {
                    AdminAuthorizationService.AdminAuthResult result =
                            adminAuthorizationService.evaluate(exchange, authentication);
                    log.debug("Verificação de admin (media) - usuário: {}, é admin: {}",
                            result.userIdentifier(), result.admin());
                    return result.admin();
                })
                .defaultIfEmpty(false);
    }
}

