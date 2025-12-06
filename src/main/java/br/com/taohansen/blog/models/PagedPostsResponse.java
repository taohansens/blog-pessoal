package br.com.taohansen.blog.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * Resposta paginada de posts.
 * 
 * Usa classe com builder para flexibilidade e imutabilidade parcial.
 * Segue padrões de APIs RESTful modernas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedPostsResponse {
    
    /**
     * Lista de metadados dos posts (pode estar vazia, nunca null).
     */
    @NotNull(message = "Lista de posts não pode ser nula")
    @Valid
    @Builder.Default
    private List<PostMetadata> posts = Collections.emptyList();
    
    /**
     * Número da página atual (0-indexed).
     */
    @Min(value = 0, message = "Página deve ser >= 0")
    private int page;
    
    /**
     * Tamanho da página (número de itens por página).
     */
    @Min(value = 1, message = "Tamanho da página deve ser >= 1")
    private int size;
    
    /**
     * Total de itens disponíveis.
     */
    @Min(value = 0, message = "Total deve ser >= 0")
    private long total;
    
    /**
     * Indica se há próxima página.
     */
    private boolean hasNext;
    
    /**
     * Calcula o número total de páginas.
     * @return Número total de páginas (0 se não houver itens)
     */
    public int getTotalPages() {
        if (size == 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / size);
    }
    
    /**
     * Verifica se há página anterior.
     * @return true se há página anterior
     */
    public boolean hasPrevious() {
        return page > 0;
    }
    
    /**
     * Verifica se a página atual está vazia.
     * @return true se não há posts na página atual
     */
    public boolean isEmpty() {
        return posts == null || posts.isEmpty();
    }
}