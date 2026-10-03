package lan_rag_backend.example.demo.entity;

import com.pgvector.PGvector;
import jakarta.persistence.*;
import lan_rag_backend.example.demo.config.PGvectorConverter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rag_document_vector")
public class RagDocumentVector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "file_last_modified", nullable = false)
    private long fileLastModified;

    @Column(name = "text_content", columnDefinition = "TEXT", nullable = false)
    private String textContent;

    @Column(name = "embedding", columnDefinition = "vector(768)")
    @JdbcTypeCode(SqlTypes.OTHER)
    @Convert(converter = PGvectorConverter.class)
    private PGvector embedding;

    public RagDocumentVector() {}

    public RagDocumentVector(String fileName, int pageNumber, int chunkIndex, long fileLastModified, String textContent, float[] floatArray) {
        this.fileName = fileName;
        this.pageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
        this.fileLastModified = fileLastModified;
        this.textContent = textContent;
        this.embedding = (floatArray != null) ? new PGvector(floatArray) : null;
    }

    // Getters et Setters
    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public int getPageNumber() { return pageNumber; }
    public int getChunkIndex() { return chunkIndex; }
    public long getFileLastModified() { return fileLastModified; }
    public String getTextContent() { return textContent; }
    public PGvector getEmbedding() {
        return embedding;
    }
    public void setEmbedding(PGvector embedding) {
        this.embedding = embedding;
    }
}
