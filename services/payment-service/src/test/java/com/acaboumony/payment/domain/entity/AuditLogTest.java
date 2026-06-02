package com.acaboumony.payment.domain.entity;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AuditLogTest {

    @Test
    void constructor_setsFields() {
        var transactionId = "txn_001";
        var actorId = UUID.randomUUID();

        var audit = new AuditLog(transactionId, "CREATED", actorId, "{\"risk\":\"low\"}", "127.0.0.1");

        assertEquals(transactionId, audit.getTransactionId());
        assertEquals("CREATED", audit.getAction());
        assertEquals(actorId, audit.getActorId());
        assertEquals("{\"risk\":\"low\"}", audit.getPayload());
        assertEquals("127.0.0.1", audit.getIpAddress());
    }

    @Test
    void constructor_withNullDetails() {
        var audit = new AuditLog("txn_001", "PAYMENT_APPROVED", null, null, null);

        assertEquals("txn_001", audit.getTransactionId());
        assertEquals("PAYMENT_APPROVED", audit.getAction());
        assertNull(audit.getActorId());
        assertNull(audit.getPayload());
        assertNull(audit.getIpAddress());
    }

    @Test
    void onCreate_setsTimestamp() {
        var audit = new AuditLog("txn_001", "CREATED", null, null, null);
        audit.onCreate();
        assertNotNull(audit.getCreatedAt());
    }

    @Test
    void getters_returnNullBeforePersist() {
        var audit = new AuditLog();
        assertNull(audit.getId());
        assertNull(audit.getTransactionId());
        assertNull(audit.getAction());
        assertNull(audit.getActorId());
        assertNull(audit.getPayload());
        assertNull(audit.getIpAddress());
        assertNull(audit.getCreatedAt());
    }
}
