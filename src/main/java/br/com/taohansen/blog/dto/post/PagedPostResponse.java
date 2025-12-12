package br.com.taohansen.blog.dto.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * DTO de resposta paginada para listagem de posts.
 * Inclui metadados de paginação e utilitários de navegação.
 */
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

    /**
     * Calcula o total de páginas com base em `total` e `size`.
     *
     * @return quantidade total de páginas
     */
    public int getTotalPages() {
        if (size == 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / size);
    }

    /**
     * Indica se há página anterior.
     *
     * @return {@code true} se a página atual for maior que zero
     */
    public boolean hasPrevious() {
        return page > 0;
    }

    /**
     * Indica se a página retornou lista vazia.
     *
     * @return {@code true} se não há posts nesta página
     */
    public boolean isEmpty() {
        return posts == null || posts.isEmpty();
    }
}
