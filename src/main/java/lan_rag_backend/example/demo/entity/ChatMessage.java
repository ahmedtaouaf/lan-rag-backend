package lan_rag_backend.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    @JsonIgnore
    private ChatRoom chatRoom;

    @Column(nullable = false)
    private String sender; // "USER" ou "ASSISTANT"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "TEXT")
    private String sources; // Format JSON ou texte libre décrivant les sources

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatMessage() {}

    public ChatMessage(ChatRoom chatRoom, String sender, String content, String sources) {
        this.chatRoom = chatRoom;
        this.sender = sender;
        this.content = content;
        this.sources = sources;
    }

    public Long getId() { return id; }
    public String getSender() { return sender; }
    public String getContent() { return content; }
    public String getSources() { return sources; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
