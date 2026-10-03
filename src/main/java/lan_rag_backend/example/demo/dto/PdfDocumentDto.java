package lan_rag_backend.example.demo.dto;

public class PdfDocumentDto {
    private String fileName;
    private long sizeBytes;

    public PdfDocumentDto(String fileName, long sizeBytes) {
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
    }

    public String getFileName() { return fileName; }
    public long getSizeBytes() { return sizeBytes; }
}
