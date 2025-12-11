package br.com.taohansen.blog.dto.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedPostResponse {
    @Builder.Default
    private List<PostSummaryResponse> posts = Collections.emptyList();
    private int page;
    private int size;
    private long total;
    private boolean hasNext;

    public int getTotalPages() {
        if (size == 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / size);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean isEmpty() {
        return posts == null || posts.isEmpty();
    }
}
