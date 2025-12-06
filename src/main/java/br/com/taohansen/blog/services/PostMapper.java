package br.com.taohansen.blog.services;

import br.com.taohansen.blog.models.Post;
import br.com.taohansen.blog.models.PostMetadata;
import br.com.taohansen.blog.models.PostsViewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Componente responsável pelo mapeamento de dados do CouchDB para modelos de domínio.
 * Segue o princípio de responsabilidade única (SRP).
 */
@Component
@Slf4j
public class PostMapper {

    private static final String FIELD_ID = "id";
    private static final String FIELD_ID_ALT = "_id";
    private static final String FIELD_REV = "_rev";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_SLUG = "slug";
    private static final String FIELD_SUMMARY = "summary";
    private static final String FIELD_TAGS = "tags";
    private static final String FIELD_DATE = "date";
    private static final String FIELD_CONTENT = "content";

    /**
     * Mapeia uma linha da resposta do CouchDB para um objeto Post completo.
     * @param row A linha da resposta do CouchDB
     * @return Post mapeado
     */
    public Post mapToPost(PostsViewResponse.Row row) {
        if (row == null || row.getDoc() == null) {
            log.warn("Tentativa de mapear post com row ou doc nulo");
            return null;
        }

        Map<String, Object> doc = row.getDoc();
        Post post = new Post();
        
        mapCommonFields(doc, post);
        post.setContent(getStringSafely(doc, FIELD_CONTENT));
        
        return post;
    }

    /**
     * Mapeia uma linha da resposta do CouchDB para um objeto PostMetadata.
     * @param row A linha da resposta do CouchDB
     * @return PostMetadata mapeado
     */
    public PostMetadata mapToMetadata(PostsViewResponse.Row row) {
        if (row == null || row.getDoc() == null) {
            log.warn("Tentativa de mapear metadata com row ou doc nulo");
            return null;
        }

        Map<String, Object> doc = row.getDoc();
        PostMetadata meta = new PostMetadata();
        
        mapCommonFields(doc, meta);
        
        return meta;
    }

    /**
     * Mapeia campos comuns entre Post e PostMetadata.
     * @param doc O documento do CouchDB
     * @param target O objeto de destino (Post ou PostMetadata)
     */
    private void mapCommonFields(Map<String, Object> doc, Object target) {
        // Buscar ID - pode ser "id" (em views) ou "_id" (em documentos diretos)
        String id = getStringSafely(doc, FIELD_ID_ALT);
        if (id == null) {
            id = getStringSafely(doc, FIELD_ID);
        }
        
        if (target instanceof Post post) {
            post.setId(id);
            post.setRevision(getStringSafely(doc, FIELD_REV));
            post.setTitle(getStringSafely(doc, FIELD_TITLE));
            post.setSlug(getStringSafely(doc, FIELD_SLUG));
            post.setSummary(getStringSafely(doc, FIELD_SUMMARY));
            post.setTags(getListSafely(doc, FIELD_TAGS));
            post.setDate(parseDateSafely(doc, FIELD_DATE));
        } else if (target instanceof PostMetadata meta) {
            meta.setId(id);
            meta.setTitle(getStringSafely(doc, FIELD_TITLE));
            meta.setSlug(getStringSafely(doc, FIELD_SLUG));
            meta.setSummary(getStringSafely(doc, FIELD_SUMMARY));
            meta.setTags(getListSafely(doc, FIELD_TAGS));
            meta.setDate(parseDateSafely(doc, FIELD_DATE));
        }
    }

    /**
     * Obtém uma string de forma segura do mapa.
     * @param doc O documento
     * @param key A chave
     * @return A string ou null se não existir ou não for string
     */
    private String getStringSafely(Map<String, Object> doc, String key) {
        Object value = doc.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        log.warn("Campo '{}' não é uma string, valor: {}", key, value);
        return String.valueOf(value);
    }

    /**
     * Obtém uma lista de strings de forma segura do mapa.
     * Valida que todos os elementos são strings.
     * @param doc O documento
     * @param key A chave
     * @return A lista ou null se não existir ou não for lista de strings
     */
    @SuppressWarnings("unchecked")
    private List<String> getListSafely(Map<String, Object> doc, String key) {
        Object value = doc.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof List list) {
            try {
                // Validar que todos os elementos são strings
                for (Object item : list) {
                    if (item != null && !(item instanceof String)) {
                        log.warn("Campo '{}' contém elementos não-string: {}", key, item.getClass().getSimpleName());
                        // Converter para string se possível, senão retornar null
                        return null;
                    }
                }
                return (List<String>) list;
            } catch (ClassCastException e) {
                log.warn("Erro ao converter campo '{}' para lista de strings: {}", key, e.getMessage());
                return null;
            }
        }
        log.warn("Campo '{}' não é uma lista, tipo: {}", key, value.getClass().getSimpleName());
        return null;
    }

    /**
     * Faz o parse de uma data de forma segura.
     * @param doc O documento
     * @param key A chave
     * @return A data ou null se não existir ou não puder ser parseada
     */
    private LocalDate parseDateSafely(Map<String, Object> doc, String key) {
        String dateStr = getStringSafely(doc, key);
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            log.warn("Erro ao fazer parse da data '{}' do campo '{}': {}", dateStr, key, e.getMessage());
            return null;
        }
    }
    
    /**
     * Mapeia um documento do CouchDB diretamente para um Post.
     * Usado quando buscamos um documento por ID.
     * @param doc O documento do CouchDB
     * @return Post mapeado
     */
    public Post mapDocumentToPost(Map<String, Object> doc) {
        if (doc == null) {
            log.warn("Tentativa de mapear post com documento nulo");
            return null;
        }

        Post post = new Post();
        mapCommonFields(doc, post);
        post.setContent(getStringSafely(doc, FIELD_CONTENT));
        
        return post;
    }
    
    /**
     * Mapeia um Post para um Map (documento do CouchDB).
     * @param post O post a ser convertido
     * @return Map representando o documento do CouchDB
     */
    public Map<String, Object> mapPostToDocument(Post post) {
        if (post == null) {
            throw new IllegalArgumentException("Post não pode ser nulo");
        }
        
        Map<String, Object> doc = new java.util.HashMap<>();
        
        // Campos obrigatórios do CouchDB
        if (post.getId() != null) {
            doc.put("_id", post.getId());
        }
        if (post.getRevision() != null) {
            doc.put("_rev", post.getRevision());
        }
        
        // Campos do post
        doc.put("type", post.getType() != null ? post.getType() : "blog_post");
        doc.put("title", post.getTitle());
        doc.put("slug", post.getSlug());
        doc.put("date", post.getDate() != null ? post.getDate().toString() : null);
        doc.put("tags", post.getTags() != null ? post.getTags() : new ArrayList<>());
        doc.put("summary", post.getSummary());
        doc.put("content", post.getContent());
        
        return doc;
    }
}

