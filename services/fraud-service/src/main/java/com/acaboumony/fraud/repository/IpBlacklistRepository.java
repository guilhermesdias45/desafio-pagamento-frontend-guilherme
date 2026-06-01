package com.acaboumony.fraud.repository;

import com.acaboumony.fraud.domain.entity.IpBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IpBlacklistRepository extends JpaRepository<IpBlacklist, UUID> {
    Optional<IpBlacklist> findByIpAddress(String ipAddress);
}
