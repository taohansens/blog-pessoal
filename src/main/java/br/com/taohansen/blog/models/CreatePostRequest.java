package br.com.taohansen.blog.models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO para criação e atualização de posts.
 * Não inclui campos técnicos como id e _rev que são gerenciados pelo sistema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {

    /**
     * Título do post.
     */
    @NotBlank(message = "Título não pode ser vazio")
    @Size(min = 1, max = 500, message = "Título deve ter entre 1 e 500 caracteres")
    private String title;

    /**
     * Slug único do post (usado na URL).
     * Se não fornecido, será gerado automaticamente a partir do título.
     */
    @Size(max = 200, message = "Slug não pode exceder 200 caracteres")
    private String slug;

    /**
     * Data e hora de publicação do post.
     * Se não fornecido, será usada a data/hora atual.
     */
    private LocalDateTime date;

    /**
     * Indica se o post é um rascunho.
     * Rascunhos só são visíveis para administradores.
     */
    @Builder.Default
    private Boolean draft = false;

    /**
     * Lista de tags do post.
     */
    @Builder.Default
    private List<@NotBlank @Size(max = 50) String> tags = new ArrayList<>();

    /**
     * Dados da imagem usada no frontend (opcional).
     */
    @Valid
    private PostImage image;

    /**
     * Resumo/descrição curta do post.
     */
    @Size(max = 1000, message = "Resumo não pode exceder 1000 caracteres")
    private String summary;

    /**
     * Conteúdo completo do post (markdown ou HTML).
     */
    @NotBlank(message = "Conteúdo não pode ser vazio")
    @Size(max = 100000, message = "Conteúdo não pode exceder 100000 caracteres")
    private String content;
}

