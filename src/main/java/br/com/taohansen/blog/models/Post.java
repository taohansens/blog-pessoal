package br.com.taohansen.blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo de domínio representando um post completo do blog.
 * Contém todas as informações do post incluindo conteúdo.
 * 
 * Segue princípios de imutabilidade parcial e validação de dados.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    
    /**
     * ID único do documento no CouchDB.
     */
    @NotBlank(message = "ID não pode ser vazio")
    @Size(max = 255, message = "ID não pode exceder 255 caracteres")
    private String id;
    
    /**
     * Revisão do documento no CouchDB (usado para controle de concorrência).
     * Não exposto na API pública por questões de segurança.
     */
    @JsonProperty("_rev")
    @JsonIgnore // Não serializar na resposta da API
    private String revision;
    
    /**
     * Tipo do documento (padrão: blog_post).
     * Usado para identificação no CouchDB.
     */
    @Builder.Default
    private String type = "blog_post";

    /**
     * Título do post.
     */
    @NotBlank(message = "Título não pode ser vazio")
    @Size(min = 1, max = 500, message = "Título deve ter entre 1 e 500 caracteres")
    private String title;
    
    /**
     * Slug único do post (usado na URL).
     */
    @NotBlank(message = "Slug não pode ser vazio")
    @Size(min = 1, max = 200, message = "Slug deve ter entre 1 e 200 caracteres")
    private String slug;
    
    /**
     * Data e hora de publicação do post.
     */
    @NotNull(message = "Data não pode ser nula")
    private LocalDateTime date;
    
    /**
     * Data e hora da última atualização do post.
     * Null se o post nunca foi atualizado.
     */
    private LocalDateTime updatedAt;
    
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