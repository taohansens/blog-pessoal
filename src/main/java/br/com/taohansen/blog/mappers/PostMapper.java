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

@Mapper(componentModel = "spring")
public interface PostMapper {

    PostResponse toResponse(Post post);

    PostSummaryResponse toSummary(PostMetadata metadata);

    List<PostSummaryResponse> toSummaryList(List<PostMetadata> metadata);

    @Mapping(target = "posts", source = "posts")
    PagedPostResponse toPagedResponse(PagedPost response);

    PostImageResponse toImage(PostImage image);
}
