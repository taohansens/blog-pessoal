package br.com.taohansen.blog.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class PostsViewResponse {
    @JsonProperty("total_rows")
    private int totalRows;
    
    private int offset;
    private List<Row> rows;

    @Data
    public static class Row {
        private String id;
        private String key;
        private Value value;
        private Map<String, Object> doc;

        @Data
        public static class Value {
            private String title;
            private String slug;
            private String summary;
            private LocalDate date;
            private List<String> tags;
        }
    }
}
