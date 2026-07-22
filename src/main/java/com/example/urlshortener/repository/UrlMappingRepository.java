package com.example.urlshortener.repository;

import com.example.urlshortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    /**
     * Atomically increments the access counter in a single UPDATE statement.
     * Doing this as a bulk update (rather than read-modify-write) avoids lost
     * updates when many redirects hit the same short code concurrently.
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.accessCount = u.accessCount + 1 WHERE u.shortCode = :shortCode")
    int incrementAccessCount(@Param("shortCode") String shortCode);
}
