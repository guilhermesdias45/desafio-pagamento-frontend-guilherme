package com.acaboumony.user.repository;

import com.acaboumony.user.domain.entity.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    Optional<Merchant> findByCnpj(String cnpj);

    boolean existsByCnpj(String cnpj);
}
