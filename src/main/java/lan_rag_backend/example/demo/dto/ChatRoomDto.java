package lan_rag_backend.example.demo.dto;

import java.time.LocalDateTime;

public record ChatRoomDto(Long id, String title, LocalDateTime createdAt) {}
