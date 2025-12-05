package br.com.taohansen.blog.models;

import lombok.Data;

import java.util.List;

@Data
public class PagedPostsResponse {
    private List<PostMetadata> posts;
    private int page;
    private int size;
    private long total;
    private boolean hasNext;
}