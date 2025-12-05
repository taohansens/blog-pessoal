package br.com.taohansen.blog.models;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PostMetadata {
    private String id;
    private String slug;
    private String title;
    private LocalDate date;
    private List<String> tags;
    private String summary;
}