package br.com.taohansen.blog.services;

import br.com.taohansen.blog.models.MediaResource;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Serviço responsável por operações de mídia no Cloudinary:
 * upload, listagem e deleção de imagens.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private static final int DEFAULT_MAX_RESULTS = 30;

    private final Cloudinary cloudinary;

    /**
     * Faz upload de uma imagem para o Cloudinary.
     *
     * @param filePart arquivo multipart de imagem
     * @return {@link Mono} com metadados da mídia criada
     */
    public Mono<MediaResource> uploadImage(FilePart filePart) {
        if (filePart == null) {
            return Mono.error(new ServerWebInputException("Arquivo é obrigatório"));
        }

        if (filePart.headers().getContentType() != null &&
                !"image".equalsIgnoreCase(Objects.requireNonNull(filePart.headers().getContentType()).getType())) {
            return Mono.error(new ServerWebInputException("Arquivo deve ser uma imagem"));
        }

        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> Mono.fromCallable(() -> {
                            Map<String, Object> uploadResult = cloudinary.uploader()
                                    .upload(bytes, ObjectUtils.asMap("resource_type", "image"));
                            return mapToResource(uploadResult);
                        })
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * Lista imagens recentes do Cloudinary, com limite máximo configurável.
     *
     * @param maxResults quantidade máxima de itens (padrão 30, máximo 100)
     * @return {@link Mono} com lista de metadados das mídias
     */
    public Mono<List<MediaResource>> listImages(Integer maxResults) {
        int limit = (maxResults == null || maxResults <= 0) ? DEFAULT_MAX_RESULTS : Math.min(maxResults, 100);

        return Mono.fromCallable(() -> cloudinary.search()
                        .expression("resource_type:image")
                        .sortBy("created_at", "desc")
                        .maxResults(limit)
                        .execute())
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> {
                    List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
                    if (resources == null) {
                        return List.of();
                    }
                    return resources.stream()
                            .map(this::mapToResource)
                            .toList();
                });
    }

    /**
     * Remove uma imagem do Cloudinary pelo seu `public_id`.
     *
     * @param publicId identificador público da imagem
     * @return {@link Mono} vazio que completa após a exclusão
     */
    public Mono<Void> deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Mono.error(new IllegalArgumentException("publicId é obrigatório"));
        }

        return Mono.fromCallable(() -> {
                    cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                    return (Void) null;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Converte a resposta do Cloudinary em {@link MediaResource}.
     *
     * @param data mapa retornado pela API do Cloudinary
     * @return metadados mapeados ou {@code null} se entrada for nula
     */
    private MediaResource mapToResource(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        MediaResource.MediaResourceBuilder builder = MediaResource.builder()
                .publicId((String) data.get("public_id"))
                .url((String) data.get("url"))
                .secureUrl((String) data.get("secure_url"))
                .format((String) data.get("format"));

        Object bytesObj = data.get("bytes");
        if (bytesObj instanceof Number num) {
            builder.bytes(num.longValue());
        }

        Object widthObj = data.get("width");
        if (widthObj instanceof Number num) {
            builder.width(num.intValue());
        }
        Object heightObj = data.get("height");
        if (heightObj instanceof Number num) {
            builder.height(num.intValue());
        }

        Object createdAtObj = data.get("created_at");
        if (createdAtObj instanceof String createdAtStr) {
            try {
                builder.createdAt(OffsetDateTime.parse(createdAtStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            } catch (Exception e) {
                log.warn("Não foi possível converter created_at: {}", createdAtStr);
            }
        }

        return builder.build();
    }
}

