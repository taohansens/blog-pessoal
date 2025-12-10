package br.com.taohansen.blog.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaResource {

    private String publicId;
    private String url;
    private String secureUrl;
    private String format;
    private Long bytes;
    private Integer width;
    private Integer height;
    private OffsetDateTime createdAt;
}

