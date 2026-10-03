package lan_rag_backend.example.demo.controller;

import lan_rag_backend.example.demo.dto.ChatRoomDto;
import lan_rag_backend.example.demo.dto.QueryRequest;
import lan_rag_backend.example.demo.dto.QueryResponse;
import lan_rag_backend.example.demo.entity.ChatMessage;
import lan_rag_backend.example.demo.entity.ChatRoom;
import lan_rag_backend.example.demo.repository.ChatMessageRepository;
import lan_rag_backend.example.demo.repository.ChatRoomRepository;
import lan_rag_backend.example.demo.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatRoomController {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RagService ragService;

    public ChatRoomController(ChatRoomRepository chatRoomRepository,
                              ChatMessageRepository chatMessageRepository,
                              RagService ragService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.ragService = ragService;
    }

    // 1. Lister tous les salons de chat
    @GetMapping("/rooms")
    public List<ChatRoomDto> getAllRooms() {
        return chatRoomRepository.findAll().stream()
                .map(room -> new ChatRoomDto(room.getId(), room.getTitle(), room.getCreatedAt()))
                .toList();
    }

    // 2. Créer un nouveau salon de chat
    @PostMapping("/rooms")
    public ResponseEntity<ChatRoom> createRoom(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "Nouvelle Conversation");
        ChatRoom room = chatRoomRepository.save(new ChatRoom(title));
        return ResponseEntity.ok(room);
    }

    // 3. Supprimer un salon de chat
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long roomId) {
        chatRoomRepository.deleteById(roomId);
        return ResponseEntity.noContent().build();
    }

    // 4. Obtenir l'historique complet des messages d'un salon
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessage>> getRoomMessages(@PathVariable Long roomId) {
        List<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId);
        return ResponseEntity.ok(messages);
    }

    // 5. Envoyer une question dans un salon (Query Rewriting + RAG + Persistence)
    @PostMapping("/send")
    public ResponseEntity<QueryResponse> sendMessage(@RequestBody QueryRequest.ChatRequest request) {
        if (request.roomId() == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        QueryResponse response = ragService.answerQuestionInRoom(request.roomId(), request.question());
        return ResponseEntity.ok(response);
    }
}
