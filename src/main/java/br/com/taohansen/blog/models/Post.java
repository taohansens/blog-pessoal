package br.com.taohansen.blog.models;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class Post {
    private String id;
    private String _rev;
    private String type = "blog_post";

    private String title;
    private String slug;
    private LocalDate date;
    private List<String> tags;
    private String summary;
    private String content;
}