package lan_rag_backend.example.demo.repository;

import lan_rag_backend.example.demo.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
}