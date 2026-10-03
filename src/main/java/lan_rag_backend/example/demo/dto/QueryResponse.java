package lan_rag_backend.example.demo.dto;

import java.util.List;

public class QueryResponse {
    private String answer;
    private List<SourceDocument> sources;

    public QueryResponse(String answer, List<SourceDocument> sources) {
        this.answer = answer;
        this.sources = sources;
    }

    public String getAnswer() { return answer; }
    public List<SourceDocument> getSources() { return sources; }

    public static class SourceDocument {
        private String fileName;
        private int pageNumber;
        private String extractedExcerpt;

        public SourceDocument(String fileName, int pageNumber, String extractedExcerpt) {
            this.fileName = fileName;
            this.pageNumber = pageNumber;
            this.extractedExcerpt = extractedExcerpt;
        }

        public String getFileName() { return fileName; }
        public int getPageNumber() { return pageNumber; }
        public String getExtractedExcerpt() { return extractedExcerpt; }
    }
}
