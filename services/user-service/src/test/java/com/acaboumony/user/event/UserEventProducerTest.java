package com.acaboumony.user.event;

import com.acaboumony.user.domain.enums.UserRole;
import com.acaboumony.user.event.payload.UserLoginBlockedEvent;
import com.acaboumony.user.event.payload.UserLoginSuccessEvent;
import com.acaboumony.user.event.payload.UserRegisteredEvent;
import com.acaboumony.user.event.payload.UserTwoFactorEnabledEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEventProducerTest {

    @Mock KafkaTemplate<String, UserEvent> kafkaTemplate;
    @InjectMocks UserEventProducer producer;

    @SuppressWarnings("unchecked")
    @Test
    void deve_publicar_UserRegisteredEvent_no_topico_correto() {
        UUID userId = UUID.randomUUID();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishUserRegistered(userId, "ana@loja.com.br", UserRole.CUSTOMER, null);

        ArgumentCaptor<UserEvent> eventCaptor = ArgumentCaptor.forClass(UserEvent.class);
        verify(kafkaTemplate).send(eq("user-events"), eq(userId.toString()), eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(UserRegisteredEvent.class);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deve_publicar_UserLoginSuccessEvent() {
        UUID userId = UUID.randomUUID();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishLoginSuccess(userId, "ana@loja.com.br", "fp-abc");

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(kafkaTemplate).send(eq("user-events"), eq(userId.toString()), captor.capture());

        assertThat(captor.getValue()).isInstanceOf(UserLoginSuccessEvent.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deve_publicar_UserLoginBlockedEvent_com_userId_real() {
        UUID userId = UUID.randomUUID();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishLoginBlocked(userId, "ana@loja.com.br", Instant.now().plusSeconds(1800));

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(kafkaTemplate).send(anyString(), eq(userId.toString()), captor.capture());

        assertThat(captor.getValue()).isInstanceOf(UserLoginBlockedEvent.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deve_usar_sentinel_UUID_quando_userId_e_null_no_loginBlocked() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishLoginBlocked(null, "naoexiste@loja.com.br", Instant.now().plusSeconds(1800));

        // Should not throw — sentinel UUID is derived from email
        verify(kafkaTemplate).send(eq("user-events"), anyString(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void deve_publicar_UserTwoFactorEnabledEvent() {
        UUID userId = UUID.randomUUID();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishTwoFactorEnabled(userId);

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(kafkaTemplate).send(eq("user-events"), eq(userId.toString()), captor.capture());

        assertThat(captor.getValue()).isInstanceOf(UserTwoFactorEnabledEvent.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void deve_logar_erro_assincronamente_sem_propagar_excecao_quando_kafka_falha() {
        UUID userId = UUID.randomUUID();
        CompletableFuture<SendResult<String, UserEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka down"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failedFuture);

        // Must not throw — fire-and-forget with async error logging
        producer.publishLoginSuccess(userId, "ana@loja.com.br", "fp");
    }
}
