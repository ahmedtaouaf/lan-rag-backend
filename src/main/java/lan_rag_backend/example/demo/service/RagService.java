package lan_rag_backend.example.demo.service;

import lan_rag_backend.example.demo.dto.PdfDocumentDto;
import lan_rag_backend.example.demo.dto.QueryResponse;
import lan_rag_backend.example.demo.entity.ChatMessage;
import lan_rag_backend.example.demo.entity.ChatRoom;
import lan_rag_backend.example.demo.entity.RagDocumentVector;
import lan_rag_backend.example.demo.repository.ChatMessageRepository;
import lan_rag_backend.example.demo.repository.ChatRoomRepository;
import lan_rag_backend.example.demo.repository.RagVectorRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagService {

    @Value("${app.pdf-directory}")
    private String pdfDirectoryPath;

    @Value("${ollama.url}")
    private String ollamaUrl;

    @Value("${ollama.model}")
    private String modelName;

    @Value("${ollama.embed-model}")
    private String embedModelName;

    private final RestTemplate restTemplate;
    private final RagVectorRepository vectorRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;

    public RagService(RestTemplateBuilder restTemplateBuilder, RagVectorRepository vectorRepository, ChatRoomRepository chatRoomRepository, ChatMessageRepository chatMessageRepository) {
        this.restTemplate = restTemplateBuilder.build();
        this.vectorRepository = vectorRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @PostConstruct
    @Transactional
    public void syncPdfFolderWithDatabase() {
        File folder = new File(pdfDirectoryPath);
        if (!folder.exists() || !folder.isDirectory()) {
            folder.mkdirs();
            return;
        }

        File[] pdfFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        List<File> currentFiles = pdfFiles != null ? Arrays.asList(pdfFiles) : Collections.emptyList();
        Set<String> currentFileNames = currentFiles.stream().map(File::getName).collect(Collectors.toSet());

        List<String> dbFileNames = vectorRepository.findAll().stream()
                .map(RagDocumentVector::getFileName)
                .distinct()
                .toList();

        for (String dbFile : dbFileNames) {
            if (!currentFileNames.contains(dbFile)) {
                vectorRepository.deleteByFileName(dbFile);
            }
        }

        for (File file : currentFiles) {
            List<RagDocumentVector> existingVectors = vectorRepository.findByFileName(file.getName());

            if (!existingVectors.isEmpty()) {
                long lastModifiedInDb = existingVectors.get(0).getFileLastModified();
                if (lastModifiedInDb == file.lastModified()) {
                    continue;
                } else {
                    vectorRepository.deleteByFileName(file.getName());
                }
            }

            indexAndSavePdf(file);
        }
    }

    private void indexAndSavePdf(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();
            List<RagDocumentVector> newVectors = new ArrayList<>();

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).trim();

                if (!pageText.isEmpty()) {
                    int chunkSize = 600;
                    int overlap = 100;
                    int chunkIndex = 0;

                    for (int i = 0; i < pageText.length(); i += (chunkSize - overlap)) {
                        int end = Math.min(i + chunkSize, pageText.length());
                        String chunkText = pageText.substring(i, end);

                        float[] embedding = getEmbedding(chunkText);
                        if (embedding != null) {
                            newVectors.add(new RagDocumentVector(
                                    file.getName(),
                                    page,
                                    chunkIndex++,
                                    file.lastModified(),
                                    chunkText,
                                    embedding
                            ));
                        }
                    }
                }
            }

            if (!newVectors.isEmpty()) {
                vectorRepository.saveAll(newVectors);
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du traitement du fichier " + file.getName() + " : " + e.getMessage());
        }
    }

    /**
     * Traitement optimisé de la question RAG avec System Prompt strict et citations
     */
    public QueryResponse answerQuestion(String userQuestion) {
        // 1. Obtenir le vecteur de la question
        float[] queryEmbedding = getEmbedding(userQuestion);
        if (queryEmbedding == null) {
            return new QueryResponse("Erreur lors de la génération du vecteur pour votre question.", Collections.emptyList());
        }

        String vectorString = Arrays.toString(queryEmbedding);

        // 2. Récupérer le Top-5 des segments les plus proches géométriquement
        List<RagDocumentVector> candidates = vectorRepository.findTopSimilarChunks(vectorString, 5);

        if (candidates.isEmpty()) {
            return new QueryResponse("Aucun document pertinent n'a été trouvé dans la base de données.", Collections.emptyList());
        }

        // 3. Ne conserver que le Top-3 le plus pertinent
        List<RagDocumentVector> selectedChunks = candidates.stream().limit(3).toList();

        // 4. Structurer le contexte enrichi avec métadonnées pour le LLM
        StringBuilder contextBuilder = new StringBuilder();
        List<QueryResponse.SourceDocument> sources = new ArrayList<>();

        for (RagDocumentVector chunk : selectedChunks) {
            contextBuilder.append("--- SOURCE START ---\n")
                    .append("Document: ").append(chunk.getFileName()).append("\n")
                    .append("Page: ").append(chunk.getPageNumber()).append("\n")
                    .append("Contenu: ").append(chunk.getTextContent()).append("\n")
                    .append("--- SOURCE END ---\n\n");

            sources.add(new QueryResponse.SourceDocument(chunk.getFileName(), chunk.getPageNumber(), chunk.getTextContent()));
        }

        // 5. System Prompt optimisé avec instructions de précision et citations
        String systemPrompt = """
            Vous êtes un assistant documentaire professionnel et rigoureux.
            Votre rôle est de répondre à la question posée en vous basant EXCLUSIVEMENT sur les extraits de documents fournis dans le CONTEXTE ci-dessous.

            RÈGLES STRICTES DE RÉPONSE :
            1. Ne faites appel à aucune connaissance externe.
            2. Si la réponse n'est pas clairement contenue dans le CONTEXTE, répondez exactement : "Désolé, les documents disponibles ne contiennent pas cette information."
            3. À la fin de chaque affirmation importante ou en fin de réponse, indiquez systématiquement la source sous la forme [Nom_Du_Fichier.pdf - Page X].
            4. Répondez de manière synthétique, claire et directe en français.

            CONTEXTE :
            %s

            QUESTION : %s

            RÉPONSE EN FRANÇAIS :
            """.formatted(contextBuilder.toString(), userQuestion);

        // 6. Génération via Ollama avec paramètres de température réduits pour limiter la créativité
        String answer = callOllamaGenerateOptimized(systemPrompt);

        return new QueryResponse(answer, sources);
    }

    private float[] getEmbedding(String text) {
        Map<String, Object> requestBody = Map.of("model", embedModelName, "prompt", text);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    ollamaUrl + "/api/embeddings", HttpMethod.POST, new HttpEntity<>(requestBody, headers), Map.class);

            if (response.getBody() != null && response.getBody().containsKey("embedding")) {
                List<Double> doubleList = (List<Double>) response.getBody().get("embedding");
                float[] floatArray = new float[doubleList.size()];
                for (int i = 0; i < doubleList.size(); i++) {
                    floatArray[i] = doubleList.get(i).floatValue();
                }
                return floatArray;
            }
        } catch (Exception e) {
            System.err.println("Erreur embedding : " + e.getMessage());
        }
        return null;
    }

    /**
     * Appel Ollama optimisé avec paramètres de température basse (0.1) pour maximiser la fidélité
     */
    private String callOllamaGenerateOptimized(String prompt) {
        Map<String, Object> options = Map.of(
                "temperature", 0.1,  // Réduit la créativité du modèle pour éviter le blabla
                "top_p", 0.9
        );

        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "prompt", prompt,
                "stream", false,
                "options", options
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    ollamaUrl + "/api/generate", HttpMethod.POST, entity, Map.class);
            if (response.getBody() != null && response.getBody().containsKey("response")) {
                return response.getBody().get("response").toString();
            }
        } catch (Exception e) {
            return "Erreur lors de la communication avec le modèle : " + e.getMessage();
        }
        return "Aucune réponse générée.";
    }

    public List<PdfDocumentDto> listFolderFiles() {
        File folder = new File(pdfDirectoryPath);
        File[] pdfFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        if (pdfFiles == null) return Collections.emptyList();

        return Arrays.stream(pdfFiles)
                .map(file -> new PdfDocumentDto(file.getName(), file.length()))
                .collect(Collectors.toList());
    }

    public File getPdfFile(String fileName) {
        File file = new File(pdfDirectoryPath, fileName);
        return file.exists() ? file : null;
    }

    @Transactional
    public QueryResponse answerQuestionInRoom(Long roomId, String userQuestion) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Salon de chat introuvable avec l'ID: " + roomId));

        // 1. Récupérer les 4 derniers messages du salon
        List<ChatMessage> recentMessages = chatMessageRepository.findRecentMessagesByRoomId(roomId, PageRequest.of(0, 4));
        Collections.reverse(recentMessages); // Remettre dans l'ordre chronologique

        // 2. Réécriture de la question si un historique existe (Query Rewriting)
        String standaloneQuestion = userQuestion;
        if (!recentMessages.isEmpty()) {
            standaloneQuestion = rewriteQuestionWithHistory(userQuestion, recentMessages);
        }

        // 3. Obtenir l'embedding de la question réécrite
        float[] queryEmbedding = getEmbedding(standaloneQuestion);
        if (queryEmbedding == null) {
            return new QueryResponse("Erreur lors de la génération du vecteur.", Collections.emptyList());
        }

        // 4. Recherche vectorielle dans PostgreSQL
        List<RagDocumentVector> candidates = vectorRepository.findTopSimilarChunks(Arrays.toString(queryEmbedding), 5);
        List<RagDocumentVector> selectedChunks = candidates.stream().limit(3).toList();

        // 5. Enregistrer le message de l'utilisateur dans le salon
        chatMessageRepository.save(new ChatMessage(room, "USER", userQuestion, null));

        if (selectedChunks.isEmpty()) {
            String fallbackAnswer = "Je ne trouve aucun document pertinent pour répondre à votre question.";
            chatMessageRepository.save(new ChatMessage(room, "ASSISTANT", fallbackAnswer, null));
            return new QueryResponse(fallbackAnswer, Collections.emptyList());
        }

        // 6. Construire le contexte avec métadonnées
        StringBuilder contextBuilder = new StringBuilder();
        List<QueryResponse.SourceDocument> sources = new ArrayList<>();

        for (RagDocumentVector chunk : selectedChunks) {
            contextBuilder.append("--- SOURCE START ---\n")
                    .append("Document: ").append(chunk.getFileName()).append("\n")
                    .append("Page: ").append(chunk.getPageNumber()).append("\n")
                    .append("Contenu: ").append(chunk.getTextContent()).append("\n")
                    .append("--- SOURCE END ---\n\n");

            sources.add(new QueryResponse.SourceDocument(chunk.getFileName(), chunk.getPageNumber(), chunk.getTextContent()));
        }

        // 7. System Prompt strict avec contexte RAG
        String systemPrompt = """
            Vous êtes un assistant documentaire professionnel.
            Répondez à la question en vous basant EXCLUSIVEMENT sur le CONTEXTE fourni ci-dessous.

            RÈGLES STRICTES :
            1. Ne faites appel à aucune connaissance externe.
            2. Si la réponse n'est pas dans le contexte, dites : "Désolé, les documents ne contiennent pas cette information."
            3. Indiquez systématiquement la source sous la forme [Nom_Du_Fichier.pdf - Page X].

            CONTEXTE :
            %s

            QUESTION DE L'UTILISATEUR : %s

            RÉPONSE :
            """.formatted(contextBuilder.toString(), userQuestion);

        String answer = callOllamaGenerate(systemPrompt, 0.1);

        // 8. Sauvegarder la réponse de l'assistant avec ses sources
        String sourcesSummary = sources.stream()
                .map(s -> s.getFileName() + " (p." + s.getPageNumber() + ")")
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        chatMessageRepository.save(new ChatMessage(room, "ASSISTANT", answer, sourcesSummary));

        return new QueryResponse(answer, sources);
    }

    private String rewriteQuestionWithHistory(String newQuestion, List<ChatMessage> history) {
        StringBuilder historyText = new StringBuilder();
        for (ChatMessage msg : history) {
            historyText.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
        }

        String prompt = """
            Compte tenu du dialogue suivant et d'une nouvelle question, réécrivez la nouvelle question pour qu'elle devienne une question AUTONOME et COMPLÈTE, compréhensible sans l'historique.
            Ne répondez pas à la question, réécrivez-la uniquement. Si la question est déjà autonome, renvoyez-la telle quelle.

            HISTORIQUE DU CHAT :
            %s

            NOUVELLE QUESTION : %s

            QUESTION RÉÉCRITE AUTONOME :
            """.formatted(historyText.toString(), newQuestion);

        String rewritten = callOllamaGenerate(prompt, 0.0).trim();
        return rewritten.isEmpty() ? newQuestion : rewritten;
    }

    private String callOllamaGenerate(String prompt, double temperature) {
        Map<String, Object> requestBody = Map.of(
                "model", modelName,
                "prompt", prompt,
                "stream", false,
                "options", Map.of("temperature", temperature)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    ollamaUrl + "/api/generate", HttpMethod.POST, new HttpEntity<>(requestBody, headers), Map.class);
            if (response.getBody() != null && response.getBody().containsKey("response")) {
                return response.getBody().get("response").toString();
            }
        } catch (Exception e) {
            return "Erreur de communication avec le modèle : " + e.getMessage();
        }
        return "";
    }


}