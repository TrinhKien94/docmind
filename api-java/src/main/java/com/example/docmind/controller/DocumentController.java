package com.example.docmind.controller;

import com.example.docmind.dto.DocumentStatusResponse;
import com.example.docmind.dto.DocumentUploadResponse;
import com.example.docmind.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Ingest API. Controller mỏng, delegate sang DocumentService. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ResponseEntity<DocumentUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.upload(file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentStatusResponse> status(@PathVariable("id") String id) {
        return ResponseEntity.ok(documentService.getStatus(id));
    }
}
