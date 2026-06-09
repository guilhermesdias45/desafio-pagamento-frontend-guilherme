package com.acaboumony.payment.domain.entity;

import com.acaboumony.payment.domain.enums.MpAccountType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MpTestAccountTest {

    @Test
    void constructor_setsFields() {
        var account = new MpTestAccount(
            MpAccountType.SELLER, 3459882808L,
            "TESTUSER1504687285327688180", "enc_password", "882808"
        );

        assertEquals(MpAccountType.SELLER, account.getType());
        assertEquals(3459882808L, account.getMpUserId());
        assertEquals("TESTUSER1504687285327688180", account.getEmail());
        assertEquals("enc_password", account.getPasswordEnc());
        assertEquals("882808", account.getVerificationCode());
    }

    @Test
    void setters_updateFields() {
        var account = new MpTestAccount(
            MpAccountType.BUYER, 3459473280L,
            "TESTUSER2899368672786037940", null, "473280"
        );

        account.setAccessTokenEnc("enc_access_token");
        account.setRefreshTokenEnc("enc_refresh_token");
        account.setPublicKey("APP_USR-public-key");
        account.setTokenExpiresAt(Instant.parse("2026-12-31T23:59:59Z"));

        assertEquals("enc_access_token", account.getAccessTokenEnc());
        assertEquals("enc_refresh_token", account.getRefreshTokenEnc());
        assertEquals("APP_USR-public-key", account.getPublicKey());
        assertEquals(Instant.parse("2026-12-31T23:59:59Z"), account.getTokenExpiresAt());
    }

    @Test
    void prePersist_setsTimestampsAndId() {
        var account = new MpTestAccount(
            MpAccountType.SELLER, 3459882808L,
            "test@testuser.com", null, null
        );

        account.prePersist();

        assertNotNull(account.getId());
        assertNotNull(account.getCreatedAt());
        assertNotNull(account.getUpdatedAt());
        assertEquals(account.getCreatedAt(), account.getUpdatedAt());
    }

    @Test
    void preUpdate_updatesTimestamp() throws Exception {
        var account = new MpTestAccount(
            MpAccountType.SELLER, 3459882808L,
            "test@testuser.com", null, null
        );
        account.prePersist();
        var originalUpdatedAt = account.getUpdatedAt();

        Thread.sleep(10);
        account.preUpdate();

        assertTrue(account.getUpdatedAt().isAfter(originalUpdatedAt));
        assertEquals(account.getCreatedAt(), account.getCreatedAt());
    }

    @Test
    void buyerConstructor_setsBuyerType() {
        var buyer = new MpTestAccount(
            MpAccountType.BUYER, 3459473280L,
            "buyer@testuser.com", null, "473280"
        );

        assertEquals(MpAccountType.BUYER, buyer.getType());
    }

    @Test
    void defaultConstructor_allowsNulls() {
        var account = new MpTestAccount();

        assertNull(account.getId());
        assertNull(account.getType());
        assertNull(account.getMpUserId());
        assertNull(account.getEmail());
        assertNull(account.getPasswordEnc());
        assertNull(account.getVerificationCode());
        assertNull(account.getAccessTokenEnc());
        assertNull(account.getRefreshTokenEnc());
        assertNull(account.getPublicKey());
        assertNull(account.getTokenExpiresAt());
        assertNull(account.getCreatedAt());
        assertNull(account.getUpdatedAt());
    }
}
