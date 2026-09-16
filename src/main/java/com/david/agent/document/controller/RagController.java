package com.david.agent.document.controller;

import com.david.agent.document.service.DocumentRagService;
import com.david.agent.document.vo.RagChunkVO;
import com.david.agent.document.vo.RagDocumentDetailVO;
import com.david.agent.document.vo.RagDocumentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final DocumentRagService documentRagService;

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RagDocumentVO> upload(@RequestPart("file") FilePart file) {
        return documentRagService.upload(file);
    }

    @GetMapping("/documents")
    public Mono<List<RagDocumentVO>> list() {
        return documentRagService.listAll();
    }

    @GetMapping("/documents/{id}")
    public Mono<RagDocumentDetailVO> getById(@PathVariable Long id) {
        return documentRagService.getById(id);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return documentRagService.delete(id);
    }

    @PostMapping("/documents/{id}/reindex")
    public Mono<RagDocumentVO> reindex(@PathVariable Long id) {
        return documentRagService.reindex(id);
    }

    @GetMapping("/search")
    public Mono<List<RagChunkVO>> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return documentRagService.search(query, limit);
    }
}
