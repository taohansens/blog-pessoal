package br.com.taohansen.blog.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dados da imagem do post usada no frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostImage {

    @NotBlank(message = "URL da imagem não pode ser vazia")
    @Size(max = 1000, message = "URL da imagem não pode exceder 1000 caracteres")
    private String url;

    @Size(max = 1000, message = "Atribuição da imagem não pode exceder 1000 caracteres")
    private String attribution;
}

