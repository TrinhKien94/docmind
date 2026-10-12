package com.example.docmind.repository;

import com.example.docmind.entity.BlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BlockRepository extends JpaRepository<BlockEntity, UUID> {

    List<BlockEntity> findByDocumentIdOrderByBlockOrderAsc(UUID documentId);
}
