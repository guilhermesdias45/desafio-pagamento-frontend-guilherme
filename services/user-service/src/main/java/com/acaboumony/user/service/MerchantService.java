package com.acaboumony.user.service;

import com.acaboumony.user.domain.entity.Merchant;
import com.acaboumony.user.domain.entity.User;
import com.acaboumony.user.domain.enums.MerchantStatus;
import com.acaboumony.user.exception.CnpjAlreadyRegisteredException;
import com.acaboumony.user.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for merchant-related operations.
 *
 * <p>Methods are designed to be called within an existing {@code @Transactional} boundary
 * (the caller's transaction), not to open their own.</p>
 */
@Service
public class MerchantService {

    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    /**
     * Creates a new merchant entity and persists it within the caller's transaction.
     *
     * @param owner       the user who owns this merchant account
     * @param companyName company/trade name
     * @param cnpj        14-digit CNPJ (already validated by {@code @Cnpj})
     * @return the saved {@link Merchant}
     * @throws CnpjAlreadyRegisteredException if the CNPJ is already taken
     */
    public Merchant createMerchant(User owner, String companyName, String cnpj) {
        if (merchantRepository.existsByCnpj(cnpj)) {
            throw new CnpjAlreadyRegisteredException();
        }
        Merchant merchant = Merchant.builder()
                .companyName(companyName)
                .cnpj(cnpj)
                .owner(owner)
                .status(MerchantStatus.ACTIVE)
                .build();
        Merchant saved = merchantRepository.save(merchant);
        log.info("Merchant created: merchantId={}, ownerId={}", saved.getId(), owner.getId());
        return saved;
    }
}
