package br.com.taohansen.blog.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadados de um post (versão reduzida sem conteúdo).
 * Usado em listagens e respostas paginadas para melhor performance.
 * 
 * Segue o padrão de separação de concerns: dados vs metadados.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostMetadata {
    
    /**
     * ID único do post.
     */
    @NotBlank(message = "ID não pode ser vazio")
    @Size(max = 255, message = "ID não pode exceder 255 caracteres")
    private String id;
    
    /**
     * Slug único do post.
     */
    @NotBlank(message = "Slug não pode ser vazio")
    @Size(min = 1, max = 200, message = "Slug deve ter entre 1 e 200 caracteres")
    private String slug;
    
    /**
     * Título do post.
     */
    @NotBlank(message = "Título não pode ser vazio")
    @Size(min = 1, max = 500, message = "Título deve ter entre 1 e 500 caracteres")
    private String title;
    
    /**
     * Data de publicação.
     */
    @NotNull(message = "Data não pode ser nula")
    private LocalDate date;
    
    /**
     * Lista de tags.
     */
    @Builder.Default
    private List<@NotBlank @Size(max = 50) String> tags = new ArrayList<>();
    
    /**
     * Resumo do post.
     */
    @Size(max = 1000, message = "Resumo não pode exceder 1000 caracteres")
    private String summary;
}