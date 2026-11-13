package com.insurance.integration.security.repository;

import com.insurance.integration.security.entity.ApiClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClient, UUID> {

    Optional<ApiClient> findByClientId(String clientId);

    Optional<ApiClient> findByClientIdAndActiveTrue(String clientId);

    Optional<ApiClient> findByClientIdAndPartnerId(String clientId, UUID partnerId);

    List<ApiClient> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId);

    boolean existsByClientId(String clientId);

    long countByPartnerIdAndActiveTrue(UUID partnerId);
}
