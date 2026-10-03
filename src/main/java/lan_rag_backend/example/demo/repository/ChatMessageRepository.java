package lan_rag_backend.example.demo.repository;

import lan_rag_backend.example.demo.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatRoomIdOrderByCreatedAtAsc(Long chatRoomId);

    // Récupère les N derniers messages pour construire la fenêtre contextuelle (Query Rewriting / Memory)
    @Query("SELECT m FROM ChatMessage m WHERE m.chatRoom.id = :roomId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentMessagesByRoomId(@Param("roomId") Long roomId, Pageable pageable);
}