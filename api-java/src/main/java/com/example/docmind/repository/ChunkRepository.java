package com.example.docmind.repository;

import com.example.docmind.entity.ChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChunkRepository extends JpaRepository<ChunkEntity, UUID>, ChunkRepositoryCustom {
}
