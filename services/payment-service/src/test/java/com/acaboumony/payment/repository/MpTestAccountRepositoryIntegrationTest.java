package com.acaboumony.payment.repository;

import com.acaboumony.payment.domain.entity.MpTestAccount;
import com.acaboumony.payment.domain.enums.MpAccountType;
import com.acaboumony.payment.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class MpTestAccountRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MpTestAccountRepository repository;

    @Test
    void saveAndFindByType() {
        var seller = new MpTestAccount(
            MpAccountType.SELLER, 3459882808L,
            "seller@test.com", "enc_pass", "882808"
        );
        repository.save(seller);

        var found = repository.findByType(MpAccountType.SELLER);

        assertTrue(found.isPresent());
        assertEquals(3459882808L, found.get().getMpUserId());
        assertEquals("seller@test.com", found.get().getEmail());
    }

    @Test
    void findByType_whenNotExists_returnsEmpty() {
        var result = repository.findByType(MpAccountType.SELLER);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByEmail() {
        var buyer = new MpTestAccount(
            MpAccountType.BUYER, 3459473280L,
            "buyer@test.com", null, "473280"
        );
        repository.save(buyer);

        var found = repository.findByEmail("buyer@test.com");

        assertTrue(found.isPresent());
        assertEquals(MpAccountType.BUYER, found.get().getType());
    }

    @Test
    void findByEmail_whenNotExists_returnsEmpty() {
        var result = repository.findByEmail("nonexistent@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void existsByType_returnsTrue_whenExists() {
        repository.save(new MpTestAccount(
            MpAccountType.SELLER, 3459882808L,
            "seller@test.com", null, null
        ));

        assertTrue(repository.existsByType(MpAccountType.SELLER));
    }

    @Test
    void existsByType_returnsFalse_whenNotExists() {
        assertFalse(repository.existsByType(MpAccountType.BUYER));
    }

    @Test
    void saveBuyerAndSeller() {
        var seller = new MpTestAccount(
            MpAccountType.SELLER, 3459882808L,
            "seller@test.com", "enc_pass", "882808"
        );
        var buyer = new MpTestAccount(
            MpAccountType.BUYER, 3459473280L,
            "buyer@test.com", null, "473280"
        );

        repository.save(seller);
        repository.save(buyer);

        assertEquals(2, repository.count());
    }

    @Test
    void save_fails_whenDuplicateEmail() {
        var first = new MpTestAccount(
            MpAccountType.BUYER, 3459473280L,
            "dup@test.com", null, null
        );
        repository.save(first);

        var second = new MpTestAccount(
            MpAccountType.BUYER, 3459473281L,
            "dup@test.com", null, null
        );

        assertThrows(Exception.class, () -> repository.save(second));
    }
}
