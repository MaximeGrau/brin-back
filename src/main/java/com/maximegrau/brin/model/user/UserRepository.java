package com.maximegrau.brin.model.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** @param email déjà normalisée ({@link User#normalizeEmail}) */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select i.user from UserIdentity i where i.provider = :provider and i.subject = :subject")
    Optional<User> findByIdentity(AuthProvider provider, String subject);
}
