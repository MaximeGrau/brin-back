package com.maximegrau.brin.model.video;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreatorRepository extends JpaRepository<Creator, UUID> {

    Optional<Creator> findBySourceAndHandleIgnoreCase(VideoSource source, String handle);

    List<Creator> findBySourceAndEnabledTrue(VideoSource source);

    Optional<Creator> findByUserId(UUID userId);
}
