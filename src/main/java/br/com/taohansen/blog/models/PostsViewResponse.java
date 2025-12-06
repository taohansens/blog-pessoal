package br.com.taohansen.blog.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO de resposta da view do CouchDB.
 * Representa a estrutura de resposta padrão do CouchDB para queries de views.
 * 
 * Este é um DTO de integração e não deve ser usado diretamente na API pública.
 */
@Data
public class PostsViewResponse {
    
    /**
     * Número total de linhas na view (antes da paginação).
     */
    @JsonProperty("total_rows")
    private int totalRows;
    
    /**
     * Offset usado na paginação.
     */
    private int offset;
    
    /**
     * Lista de linhas retornadas pela view.
     */
    private List<Row> rows;

    /**
     * Representa uma linha da resposta da view do CouchDB.
     */
    @Data
    public static class Row {
        /**
         * ID do documento.
         */
        private String id;
        
        /**
         * Chave usada na view (pode ser qualquer tipo).
         */
        private String key;
        
        /**
         * Valor calculado pela view (opcional).
         */
        private Value value;
        
        /**
         * Documento completo (quando include_docs=true).
         */
        private Map<String, Object> doc;

        /**
         * Valor calculado pela view (estrutura específica).
         */
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
