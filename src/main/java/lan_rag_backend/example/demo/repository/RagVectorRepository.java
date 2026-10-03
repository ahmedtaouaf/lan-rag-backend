package lan_rag_backend.example.demo.repository;

import lan_rag_backend.example.demo.entity.RagDocumentVector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface RagVectorRepository extends JpaRepository<RagDocumentVector, Long> {

    // Vérifier si un fichier est déjà indexé
    List<RagDocumentVector> findByFileName(String fileName);

    // Supprimer les vecteurs d'un fichier obsolète ou supprimé
    @Transactional
    @Modifying
    void deleteByFileName(String fileName);

    // Recherche vectorielle des Top-K segments les plus proches en Similitude Cosinus
    @Query(value = """
        SELECT *, (1 - (embedding <=> CAST(:queryVector AS vector))) AS similarity_score
        FROM rag_document_vector
        ORDER BY embedding <=> CAST(:queryVector AS vector) ASC
        LIMIT :topK
        """, nativeQuery = true)
    List<RagDocumentVector> findTopSimilarChunks(@Param("queryVector") String queryVector, @Param("topK") int topK);
}
