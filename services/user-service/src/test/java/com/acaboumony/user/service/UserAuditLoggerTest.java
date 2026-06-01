package com.acaboumony.user.service;

import com.acaboumony.user.repository.UserAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAuditLoggerTest {

    @Mock UserAuditLogRepository auditLogRepository;
    @InjectMocks UserAuditLogger auditLogger;

    @Test
    void deve_persistir_entrada_de_audit_log() {
        UUID userId = UUID.randomUUID();

        auditLogger.log(userId, "LOGIN_SUCCESS", "192.168.1.1", "fp-abc");

        verify(auditLogRepository).save(any());
    }

    @Test
    void deve_persistir_com_campos_nulos() {
        auditLogger.log(null, "LOGIN_BLOCKED", null, null);

        verify(auditLogRepository).save(any());
    }

    @Test
    void deve_nao_propagar_excecao_quando_repositorio_falha() {
        doThrow(new RuntimeException("DB down")).when(auditLogRepository).save(any());

        // Must not throw — audit logging never fails the main flow
        auditLogger.log(UUID.randomUUID(), "LOGOUT", null, null);
    }
}
