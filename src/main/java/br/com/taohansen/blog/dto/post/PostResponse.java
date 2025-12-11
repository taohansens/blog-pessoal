package br.com.taohansen.blog.dto.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostResponse {
    private String id;
    private String slug;
    private String title;
    private LocalDateTime date;
    private LocalDateTime updatedAt;
    private Boolean draft;
    private List<String> tags;
    private PostImageResponse image;
    private String summary;
    private String content;
}
