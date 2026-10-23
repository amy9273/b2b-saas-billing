package com.enterprise.billing.repository;

import com.enterprise.billing.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsByCompanyNameIgnoreCase(String companyName);
}
