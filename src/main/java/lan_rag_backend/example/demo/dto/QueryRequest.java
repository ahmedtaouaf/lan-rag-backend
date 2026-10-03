package lan_rag_backend.example.demo.dto;

public class QueryRequest {
    private String question;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public record ChatRequest(Long roomId, String question) {}
}
