package lan_rag_backend.example.demo.controller;

import lan_rag_backend.example.demo.dto.PdfDocumentDto;
import lan_rag_backend.example.demo.dto.QueryRequest;
import lan_rag_backend.example.demo.dto.QueryResponse;
import lan_rag_backend.example.demo.service.RagService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @GetMapping("/files")
    public List<PdfDocumentDto> getFiles() {
        return ragService.listFolderFiles();
    }

    @PostMapping("/reindex")
    public ResponseEntity<String> reindex() {
        ragService.syncPdfFolderWithDatabase();
        return ResponseEntity.ok("Folder re-indexed successfully.");
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        return ResponseEntity.ok(ragService.answerQuestion(request.getQuestion()));
    }

    @GetMapping("/pdf/{fileName}")
    public ResponseEntity<Resource> getPdf(@PathVariable String fileName) {
        File file = ragService.getPdfFile(fileName);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}
