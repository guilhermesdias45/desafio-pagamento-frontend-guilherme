# Teste E2E R$25 com Webhook — MercadoPago Sandbox

## Visão Geral

Fluxo completo: criar pedido → processar pagamento → **receber ambos os webhooks com HMAC válido** → verificar order status.

**Valor:** R$ 25,00 (2500 cents)
**Cartão:** Mastercard `5031 4332 1540 6351` / Cardholder `APRO` → aprovado
**Objetivo principal:** `payment.created` + `payment.updated` com HMAC 200, sem `401`/`403`.

> **Limitação do sandbox:** Com cartão `APRO` o pagamento é aprovado instantaneamente (criação e
> aprovação no mesmo segundo). Como não há transição `pending → approved`, o Mercado Pago **não
> dispara** `payment.updated` automaticamente. Em produção (cartão real com processamento
> gradual) o evento será enviado naturalmente. A validação do `payment.updated` com HMAC deve
> ser feita manualmente via **Simular** no MP Panel, conforme passo 8.

## Identificadores Fixos (reutilizar)

| Recurso | ID |
|---------|----|
| CUSTOMER email | `cliente.webhook@teste.com` |
| CUSTOMER ID | `50fd74a8-f749-430b-b991-c719e8d73ff4` |
| MERCHANT_OWNER email | `lojista.webhook@teste.com` |
| MERCHANT_OWNER ID | `612123ca-d46b-4252-88f3-2716e2b5b08a` |
| MERCHANT ID | `435f6fde-7346-483b-be17-7a0c50ee449e` |
| MP Access Token | `TEST-6025083406574896-060810-3f27c3e234e95149c11811b516be6ea2-567831283` |
| Webhook Secret | `873077e4a750989f0a93002c01fc28047ef90e196f9bcb8799626e1fa38c24c7` |
| Internal Secret | `afwh45HmU0wPZEFx01xxzYcqZWKAQVW6BuvDnP50` |
| ngrok URL | `https://squabble-engulf-ocean.ngrok-free.dev` |

## Pré-requisitos

- [ ] Docker stack rodando — `docker compose ps` (10 containers healthy)
- [ ] ngrok tunnel ativo — `curl -s http://127.0.0.1:4040/api/tunnels`
- [ ] Webhook URL configurada no MP Developer Panel (modo teste)
- [ ] Usuários existem — pular registro se já criados na sessão

## Passo a Passo

### 1. Preparar arquivos auxiliares (PowerShell)

```powershell
# Login JSON
Set-Content -Path "$env:TEMP\login.json" -Value '{"email":"cliente.webhook@teste.com","password":"Str0ng!Pass"}'

# Order JSON (R$ 25,00)
Set-Content -Path "$env:TEMP\order-r25.json" -Value '{"merchantId":"435f6fde-7346-483b-be17-7a0c50ee449e","items":[{"productId":"prod_teste_r25","description":"Teste Webhook R$25","quantity":1,"unitPriceInCents":2500}]}'

# Card Token JSON (APRO = approved)
Set-Content -Path "$env:TEMP\cardtoken.json" -Value '{"card_number":"5031433215406351","expiration_month":"12","expiration_year":"2030","security_code":"123","cardholder":{"name":"APRO"}}'
```

### 2. Login CUSTOMER

```powershell
$response = curl.exe -s -X POST http://localhost:8081/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d "@$env:TEMP\login.json" | ConvertFrom-Json
$accessToken = $response.accessToken
Write-Host "JWT: $accessToken"
```
> Esperado: JWT válido, `tokenType: Bearer`

### 3. Iniciar monitoramento de logs (segundo terminal)

```powershell
cd C:\Users\guilherme.dias\Downloads\desafio-pagamento\desafio-pagamento
docker compose logs payment-service --tail=0 --follow
```

### 4. Criar pedido de R$ 25,00

```powershell
$order = curl.exe -s -X POST http://localhost:8083/api/v1/orders `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $accessToken" `
  -H "X-User-Id: 50fd74a8-f749-430b-b991-c719e8d73ff4" `
  -H "X-User-Email: cliente.webhook@teste.com" `
  -H "Idempotency-Key: $(New-Guid)" `
  -d "@$env:TEMP\order-r25.json" | ConvertFrom-Json
$orderId = $order.data.orderId
$orderStatus = $order.data.status
Write-Host "orderId: $orderId, status: $orderStatus"
```
> Esperado: `status: PENDING`

### 5. Gerar card token no MP

```powershell
$cardTokenResponse = curl.exe -s -X POST https://api.mercadopago.com/v1/card_tokens `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer TEST-6025083406574896-060810-3f27c3e234e95149c11811b516be6ea2-567831283" `
  -d "@$env:TEMP\cardtoken.json" | ConvertFrom-Json
$cardToken = $cardTokenResponse.id
Write-Host "cardToken: $cardToken"
```
> Esperado: token de 32 caracteres hex

### 6. Processar pagamento

```powershell
$idemKey = [guid]::NewGuid().ToString()
Set-Content -Path "$env:TEMP\payment-r25.json" -Value "{`"amountInCents`":2500,`"currency`":`"BRL`",`"customerId`":`"50fd74a8-f749-430b-b991-c719e8d73ff4`",`"orderId`":`"$orderId`",`"cardToken`":`"$cardToken`",`"paymentMethodId`":`"master`",`"installments`":1,`"idempotencyKey`":`"$idemKey`"}"

$payment = curl.exe -s -X POST http://localhost:8082/api/v1/transactions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $accessToken" `
  -H "X-User-Email: cliente.webhook@teste.com" `
  -H "X-Merchant-Id: 435f6fde-7346-483b-be17-7a0c50ee449e" `
  -H "X-Forwarded-For: 192.168.1.100" `
  -d "@$env:TEMP\payment-r25.json" | ConvertFrom-Json
$mpPaymentId = $payment.data.mpPaymentId
$status = $payment.data.status
Write-Host "mpPaymentId: $mpPaymentId, status: $status"
```
> Esperado: `status: APPROVED`, `mpPaymentId` numérico

### 7. Monitorar webhooks (logs)

No terminal de logs, observar em até 60 segundos:

```
# Evento 1 — Criação
Received MP webhook: type=payment
Handling MP webhook: paymentId=<mpPaymentId>, action=payment.created

# Evento 2 — Aprovação (apenas em produção ou com cartão de teste com delay)
Received MP webhook: type=payment
Handling MP webhook: paymentId=<mpPaymentId>, action=payment.updated
Transaction <txn> updated to APPROVED via webhook
```

> ⚠️ **Comportamento do sandbox:** Cartões de teste como `APRO` aprovam instantaneamente.
> O MP só envia `payment.updated` quando há transição de estado (`pending → approved`).  
> Se após 60s o `payment.updated` não chegou, vá para o **passo 8** e use o Simulador.

**Regras de validação:**
- Se aparecer `HMAC mismatch` → algoritmo de assinatura errado
- Se aparecer `401` → webhook secret não configurado
- Se aparecer `403` → HMAC não correspondeu
- Se aparecer `200` → sucesso (assinatura válida)

### 8. Simular webhook manual (se `payment.updated` não chegar em 60s)

Acessar o MP Developer Panel:
1. Ir em **Tus integraciones** → app **6025083406574896** → **Webhooks**
2. Clicar em **Simular**
3. Selecionar evento: **Pagamentos** → `payment.updated` → status `approved`
4. Clicar em **Enviar**
5. Verificar log do payment-service — deve mostrar `action=payment.updated`

## Verificações Pós-Teste

### 9. Validar order status = PAID

```powershell
curl.exe -s http://localhost:8083/internal/orders/$orderId `
  -H "X-Internal-Secret: afwh45HmU0wPZEFx01xxzYcqZWKAQVW6BuvDnP50" | ConvertFrom-Json | Select-Object orderId, status, totalInCents
```
> Esperado: `status: PAID`

### 10. Validar transaction no banco

```powershell
docker exec aom-postgres psql -U aom -d payment_db -c "
  SELECT transaction_id, mp_payment_id, status, amount_in_cents, created_at
  FROM transactions
  WHERE mp_payment_id = $mpPaymentId;
"
```
> Esperado: `status = APPROVED`, `amount_in_cents = 2500`

### 11. Validar via API do Mercado Pago

```powershell
curl.exe -s "https://api.mercadopago.com/v1/payments/$mpPaymentId" `
  -H "Authorization: Bearer TEST-6025083406574896-060810-3f27c3e234e95149c11811b516be6ea2-567831283" | `
  ConvertFrom-Json | Select-Object id, status, status_detail, transaction_amount, date_approved
```
> Esperado: `status = approved`, `transaction_amount = 25`

## Troubleshooting

| Problema | Causa provável | Solução |
|----------|---------------|---------|
| `401` no webhook | Webhook secret vazio | Verificar `MERCADOPAGO_WEBHOOK_SECRET` no `.env` e `docker-compose.yml` |
| `403` no webhook | HMAC mismatch | Verificar algoritmo em `MercadoPagoWebhookConsumer.validateSignature()` |
| Webhook não chega | ngrok caído | Reiniciar ngrok: `ngrok http http://localhost:8082` |
| Login retorna 401 | JSON mal formatado | Usar arquivo auxiliar (`@arquivo.json`) em vez de string inline |
| Order 403 Forbidden | Header errado | Usar `X-Internal-Secret` não `Authorization: Bearer` |
| Card token expirado | Token muito velho | Gerar novo card token |

## Critérios de Sucesso

| # | Critério | Como verificar |
|---|----------|---------------|
| 1 | Pagamento `201 APPROVED` | Resposta da API de transações |
| 2 | `payment.created` recebido com 200 | Log: `action=payment.created`, sem `HMAC mismatch` |
| 3 | `payment.updated` validado (Simulador) | Log: `action=payment.updated`, sem `HMAC mismatch` |
| 4 | Nenhum `401` / `403` nos logs | `docker compose logs payment-service \| Select-String "401|403"` vazio |
| 5 | Order atualizada para `PAID` | `GET /internal/orders/{orderId}` → `status: PAID` |
| 6 | Transaction `APPROVED` no BD | `SELECT status FROM transactions` |
| 7 | Valor correto no MP | `transaction_amount = 25` na API MP |

## Resultados da Execução (11/06/2026)

| # | Critério | Resultado |
|---|----------|-----------|
| 1 | Pagamento `201 APPROVED` | ✅ `mpPaymentId=1347144359`, `status=APPROVED` |
| 2 | `payment.created` recebido | ✅ HMAC 200, `action=payment.created` |
| 3 | `payment.updated` validado | ✅ HMAC 200 via Simulador (ID 123456, `action=payment.updated`) |
| 4 | Nenhum `401`/`403`/`HMAC mismatch` | ✅ Logs limpos |
| 5 | Order `PAID` | ✅ `orderId=35795204-9505-4f58-b8d4-4fa79b0a9652`, `status=PAID` |
| 6 | Transaction `APPROVED` | ✅ `txn_1f5229e0`, `status=APPROVED`, `amount_in_cents=2500` |
| 7 | Valor MP | ✅ `transaction_amount=25`, `status=approved` |

> **Nota:** O `payment.updated` automático não veio porque o cartão `APRO` aprova
> instantaneamente no sandbox — não há transição de estado. O evento foi validado
> manualmente via Simulador do MP Panel com HMAC 200. Em produção o comportamento
> será automático.

## Rollback

```powershell
# Parar ngrok
taskkill /IM ngrok.exe /F

# Reverter alterações no código (se houver)
git checkout -- services/payment-service/src/main/java/...

# Rebuild do payment-service
docker compose up -d --build payment-service
```
