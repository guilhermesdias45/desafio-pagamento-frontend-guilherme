# Teste E2E R$50 com Webhook — MercadoPago Sandbox

## Visão Geral

Fluxo completo: criar pedido → processar pagamento → validar webhook → verificar order status.

**Valor:** R$ 50,00 (5000 cents)
**Cartão:** Mastercard `5031 4332 1540 6351` / Cardholder `APRO` → aprovado

## Pré-requisitos

- [ ] Docker stack rodando (10 containers healthy)
- [ ] `MERCADOPAGO_WEBHOOK_SECRET` configurado no `.env`
- [ ] Webhook URL registrada no MP Developer Panel
- [ ] ngrok tunnel ativo → `localhost:8082`
- [ ] `MERCADOPAGO_TIMEOUT_MS=10000`

## Passo a Passo

### 1. Rebuild payment-service

```powershell
docker compose --profile app up --build -d payment-service
docker compose logs payment-service --tail=20
```

### 2. Verificar ngrok

```powershell
curl.exe -s http://127.0.0.1:4040/api/tunnels | ConvertFrom-Json | Select-Object -ExpandProperty tunnels | Select-Object public_url
```

### 3. Registrar CUSTOMER

```powershell
$customer = curl.exe -s -X POST http://localhost:8080/api/v1/auth/register `
  -H "Content-Type: application/json" `
  -d '{"email":"cliente.webhook@teste.com","password":"Str0ng!Pass","name":"Cliente Webhook","role":"CUSTOMER"}'
$customerId = ($customer | ConvertFrom-Json).data.userId
Write-Host "customerId: $customerId"
```

### 4. Registrar MERCHANT_OWNER

```powershell
$merchant = curl.exe -s -X POST http://localhost:8080/api/v1/auth/register `
  -H "Content-Type: application/json" `
  -d '{"email":"lojista.webhook@teste.com","password":"Str0ng!Pass","name":"Lojista Webhook","role":"MERCHANT_OWNER","cnpj":"11222333000181"}'
$merchantId = ($merchant | ConvertFrom-Json).data.merchantId
$merchantUserId = ($merchant | ConvertFrom-Json).data.userId
Write-Host "merchantId: $merchantId"
```

### 5. Confirmar emails via Redis

```powershell
$tokenCliente = docker exec aom-redis redis-cli GET "email:confirm:cliente.webhook@teste.com"
curl.exe -s -X POST http://localhost:8080/api/v1/auth/confirm-email `
  -H "Content-Type: application/json" `
  -d "{`"email`":`"cliente.webhook@teste.com`",`"token`":`"$tokenCliente`"}"

$tokenLojista = docker exec aom-redis redis-cli GET "email:confirm:lojista.webhook@teste.com"
curl.exe -s -X POST http://localhost:8080/api/v1/auth/confirm-email `
  -H "Content-Type: application/json" `
  -d "{`"email`":`"lojista.webhook@teste.com`",`"token`":`"$tokenLojista`"}"
```

### 6. Login como CUSTOMER

```powershell
$login = curl.exe -s -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"cliente.webhook@teste.com","password":"Str0ng!Pass"}'
$accessToken = ($login | ConvertFrom-Json).data.accessToken
Write-Host "JWT obtido"
```

### 7. Criar pedido de R$ 50,00

```powershell
$order = curl.exe -s -X POST http://localhost:8080/api/v1/orders `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $accessToken" `
  -H "X-User-Id: $customerId" `
  -H "X-User-Email: cliente.webhook@teste.com" `
  -H "Idempotency-Key: $(New-Guid)" `
  -d "{`"merchantId`":`"$merchantId`",`"items`":[{`"productId`":`"prod_teste_webhook`",`"description`":`"Teste Webhook R$50`",`"quantity`":1,`"unitPriceInCents`":5000}]}"
$orderId = ($order | ConvertFrom-Json).data.orderId
Write-Host "orderId: $orderId, status: $($order | ConvertFrom-Json).data.status"
```

### 8. Iniciar monitoramento dos logs (webhook)

Abra um **segundo terminal** e execute:

```powershell
cd C:\Users\guilherme.dias\Downloads\desafio-pagamento\desafio-pagamento
docker compose logs payment-service --tail=0 --follow
```

### 9. Gerar card token

```powershell
$cardTokenResponse = curl.exe -s -X POST https://api.mercadopago.com/v1/card_tokens `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer TEST-6025083406574896-060810-3f27c3e234e95149c11811b516be6ea2-567831283" `
  -d '{"card_number":"5031433215406351","expiration_month":"12","expiration_year":"2030","security_code":"123","cardholder":{"name":"APRO"}}'
$cardToken = ($cardTokenResponse | ConvertFrom-Json).id
Write-Host "cardToken: $cardToken"
```

### 10. Processar pagamento

```powershell
$payment = curl.exe -s -X POST http://localhost:8080/api/v1/transactions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $accessToken" `
  -H "X-User-Email: cliente.webhook@teste.com" `
  -H "X-Merchant-Id: $merchantId" `
  -H "X-Forwarded-For: 192.168.1.100" `
  -d "{`"amountInCents`":5000,`"currency`":`"BRL`",`"customerId`":`"$customerId`",`"orderId`":`"$orderId`",`"cardToken`":`"$cardToken`",`"paymentMethodId`":`"master`",`"installments`":1,`"idempotencyKey`":`"$(New-Guid)`"}"
$payment | ConvertFrom-Json | ConvertTo-Json -Depth 10
$mpPaymentId = ($payment | ConvertFrom-Json).data.mpPaymentId
Write-Host "mpPaymentId: $mpPaymentId"
```

### 11. Validar webhook nos logs

No segundo terminal devem aparecer:

```
# Evento 1 — Criação
Received MP webhook: type=payment
Handling MP webhook: paymentId=<mpPaymentId>, action=payment.created

# Evento 2 — Aprovação
Received MP webhook: type=payment
Handling MP webhook: paymentId=<mpPaymentId>, action=payment.updated
Transaction <txn> updated to APPROVED via webhook
```

**Não pode** aparecer `401`, `403`, ou `HMAC mismatch`.

### 12. Validar order status = PAID

```powershell
curl.exe -s http://localhost:8083/internal/orders/$orderId `
  -H "Authorization: Bearer afwh45HmU0wPZEFx01xxzYcqZWKAQVW6BuvDnP50"
# Esperado: "status": "PAID"
```

### 13. Validar transaction no BD

```powershell
docker exec aom-postgres psql -U aom -d payment_db -c "
  SELECT transaction_id, mp_payment_id, status, amount_in_cents
  FROM transactions
  WHERE mp_payment_id = $mpPaymentId;
"
# Esperado: status = 'APPROVED'
```

### 14. Validar via API MP

```powershell
curl.exe -s "https://api.mercadopago.com/v1/payments/$mpPaymentId" `
  -H "Authorization: Bearer TEST-6025083406574896-060810-3f27c3e234e95149c11811b516be6ea2-567831283" | `
  ConvertFrom-Json | Select-Object status, transaction_amount, collector_id
```

## Critérios de Sucesso

| # | Critério | Verificação |
|---|----------|-------------|
| 1 | Pagamento retorna `201 APPROVED` | Resposta da API |
| 2 | Webhook `payment.created` recebido | Log: `action=payment.created` |
| 3 | Webhook `payment.updated (approved)` recebido | Log: `updated to APPROVED via webhook` |
| 4 | Nenhum `401` ou `403` nos logs | Log livre de erros de assinatura |
| 5 | Order atualizada para `PAID` via Kafka | `SELECT status FROM orders` |
| 6 | Transaction no BD com status `APPROVED` | `SELECT status FROM transactions` |

## Rollback

- Parar ngrok: `taskkill /IM ngrok.exe /F`
- Reverter código: `git checkout -- services/payment-service/src/main/java/...`
- Rebuild: `docker compose --profile app up --build -d payment-service`
