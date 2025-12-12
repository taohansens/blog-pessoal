package br.com.taohansen.blog.mappers;

import br.com.taohansen.blog.dto.post.PagedPostResponse;
import br.com.taohansen.blog.dto.post.PostImageResponse;
import br.com.taohansen.blog.dto.post.PostResponse;
import br.com.taohansen.blog.dto.post.PostSummaryResponse;
import br.com.taohansen.blog.models.PagedPost;
import br.com.taohansen.blog.models.Post;
import br.com.taohansen.blog.models.PostImage;
import br.com.taohansen.blog.models.PostMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper para converter modelos de domínio em DTOs de resposta.
 */
@Mapper(componentModel = "spring")
public interface PostMapper {

    /** Converte um {@link Post} para {@link PostResponse}. */
    PostResponse toResponse(Post post);

    /** Converte metadados de post em resumo. */
    PostSummaryResponse toSummary(PostMetadata metadata);

    /** Converte lista de metadados em lista de resumos. */
    List<PostSummaryResponse> toSummaryList(List<PostMetadata> metadata);

    /** Converte um {@link PagedPost} para {@link PagedPostResponse}. */
    @Mapping(target = "posts", source = "posts")
    PagedPostResponse toPagedResponse(PagedPost response);

    /** Converte imagem de post para DTO de imagem. */
    PostImageResponse toImage(PostImage image);
}
