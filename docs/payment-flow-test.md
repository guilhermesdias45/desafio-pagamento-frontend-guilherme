# Teste de Fluxo de Pagamento — MercadoPago Sandbox

## 1. Pré-requisitos

- Docker stack rodando com 10 containers healthy
- `MERCADOPAGO_ACCESS_TOKEN` válido no `.env` (sandbox TEST-...)
- `docker exec`, `curl.exe` disponíveis

## 2. Visão Geral do Fluxo

```
Registrar CUSTOMER → Confirmar email (Redis) → Login (JWT)
Registrar MERCHANT_OWNER → Confirmar email (Redis)
Criar pedido → Gerar cardToken (API MP) → Processar pagamento
```

## 3. Cartões de Teste — MercadoPago Sandbox

| Bandeira | Número | Parcelas | Cardholder | Resultado |
|----------|--------|----------|-----------|-----------|
| Mastercard | `5031 4332 1540 6351` | 1 | `APRO` | Aprovado ✅ |
| Mastercard | `5031 4332 1540 6351` | 1 | `OTRO` | Recusado ❌ |
| Visa | `4235 6477 2802 5682` | 1 | `APRO` | Aprovado ✅ |
| Visa | `4915 8234 4490 1499` | 1 | `APRO` | Aprovado ✅ |

> O cardholder name é determinante. `APRO` = pagamento aprovado, `OTRO` = recusado.

## 4. Passo a Passo

### 4.1 Extrair Access Token do `.env`

```powershell
$env:MP_TOKEN = "TEST-CHANGE_ME"
```

### 4.2 Registrar CUSTOMER

```powershell
$customer = curl.exe -s -X POST http://localhost:8080/api/v1/auth/register `
  -H "Content-Type: application/json" `
  -d '{"email":"cliente.pag@teste.com","password":"Str0ng!Pass","name":"Cliente Pagamento","role":"CUSTOMER"}'
$customerId = ($customer | ConvertFrom-Json).data.userId
```

**Esperado:** HTTP 201, `status: "REGISTERED"`, retorna `userId`.

### 4.3 Registrar MERCHANT_OWNER

```powershell
$merchant = curl.exe -s -X POST http://localhost:8080/api/v1/auth/register `
  -H "Content-Type: application/json" `
  -d '{"email":"lojista.pag@teste.com","password":"Str0ng!Pass","name":"Lojista Pagamento","role":"MERCHANT_OWNER","cnpj":"11222333000181"}'
$merchantId = ($merchant | ConvertFrom-Json).data.merchantId
$merchantUserId = ($merchant | ConvertFrom-Json).data.userId
```

**Esperado:** HTTP 201, retorna `merchantId` + `userId`.

### 4.4 Confirmar Emails (via Redis)

```powershell
# Confirmar CUSTOMER
$tokenCliente = docker exec aom-redis redis-cli GET "email:confirm:cliente.pag@teste.com"
curl.exe -s -X POST http://localhost:8080/api/v1/auth/confirm-email `
  -H "Content-Type: application/json" `
  -d "{`"email`":`"cliente.pag@teste.com`",`"token`":`"$tokenCliente`"}"

# Confirmar MERCHANT_OWNER  
$tokenLojista = docker exec aom-redis redis-cli GET "email:confirm:lojista.pag@teste.com"
curl.exe -s -X POST http://localhost:8080/api/v1/auth/confirm-email `
  -H "Content-Type: application/json" `
  -d "{`"email`":`"lojista.pag@teste.com`",`"token`":`"$tokenLojista`"}"
```

**Esperado:** HTTP 200.

### 4.5 Login como CUSTOMER

```powershell
$login = curl.exe -s -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"cliente.pag@teste.com","password":"Str0ng!Pass"}'
$accessToken = ($login | ConvertFrom-Json).data.accessToken
```

**Esperado:** HTTP 200, retorna `accessToken` (JWT).

### 4.6 Criar Pedido

```powershell
$order = curl.exe -s -X POST http://localhost:8080/api/v1/orders `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $accessToken" `
  -H "X-User-Id: $customerId" `
  -H "X-User-Email: cliente.pag@teste.com" `
  -H "Idempotency-Key: $(New-Guid)" `
  -d "{`"merchantId`":`"$merchantId`",`"items`":[{`"productId`":`"prod_vestido_azul`",`"description`":`"Vestido Azul Floral Tam M`",`"quantity`":1,`"unitPriceInCents`":8990}]}"
$orderId = ($order | ConvertFrom-Json).data.orderId
```

**Esperado:** HTTP 201, `status: "PENDING"`, `totalInCents: 8990`.

### 4.7 Gerar Card Token (MercadoPago API)

Chama a API de card tokens do MercadoPago com um cartão de teste:

```powershell
$cardTokenResponse = curl.exe -s -X POST https://api.mercadopago.com/v1/card_tokens `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $env:MP_TOKEN" `
  -d '{"card_number":"5031433215406351","expiration_month":"12","expiration_year":"2030","security_code":"123","cardholder":{"name":"APRO"}}'
$cardToken = ($cardTokenResponse | ConvertFrom-Json).id
```

**Esperado:** HTTP 201, retorna `id` (32-char hex token).

### 4.8 Processar Pagamento

```powershell
$payment = curl.exe -s -X POST http://localhost:8080/api/v1/transactions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $accessToken" `
  -H "X-User-Email: cliente.pag@teste.com" `
  -H "X-Merchant-Id: $merchantId" `
  -H "X-Forwarded-For: 192.168.1.100" `
  -d "{`"amountInCents`":8990,`"currency`":`"BRL`",`"customerId`":`"$customerId`",`"orderId`":`"$orderId`",`"cardToken`":`"$cardToken`",`"paymentMethodId`":`"master`",`"installments`":1,`"idempotencyKey`":`"$(New-Guid)`"}"
$payment | ConvertFrom-Json | ConvertTo-Json -Depth 10
```

**Esperado:** HTTP 201, `data.status: "APPROVED"`, `data.mpPaymentId` presente.

## 5. Cenários de Teste

| # | Cenário | Cardholder | Resultado Esperado | HTTP |
|---|---------|-----------|-------------------|------|
| 1 | Pagamento aprovado | `APRO` | `APPROVED`, `mpPaymentId` ≠ null | 201 |
| 2 | Cartão recusado | `OTRO` | `CARD_DECLINED` | 422 |
| 3 | Idempotency duplicado | `APRO` | Mesmo resultado da requisição original | 200 |
| 4 | Pedido inexistente | — | `ORDER_NOT_FOUND` | 404 |
| 5 | Cliente inexistente | — | `CUSTOMER_NOT_FOUND` | 404 |
| 6 | Moeda inválida (USD) | — | `INVALID_CURRENCY` | 400 |
| 7 | Token inválido | — | `CARD_DECLINED` | 422 |

## 6. Troubleshooting

| Erro | Causa | Solução |
|------|-------|---------|
| `502 Bad Gateway` | API Gateway sem rota | Verificar `docker logs aom-api-gateway` |
| `503 MP_GATEWAY_TIMEOUT` | MP API não respondeu em 800ms | Aumentar `MERCADOPAGO_TIMEOUT_MS` para ≥ 5000ms no `docker-compose.yml` |
| `401 Unauthorized` | JWT ausente/expirado | Refazer login |
| `Rate limited` | >100 req/min | Aguardar 1 minuto |
| `ORDER_NOT_PENDING` | Pedido expirado (15min) | Criar novo pedido |
| Transaction não aparece | Kafka indisponível | Verificar `docker logs aom-kafka` |

## 7. Resultados da Execução

Executado em: `2026-06-08T14:35-14:50 UTC` | Branch: `dev-1` | Ambiente: Docker Desktop (Windows)

| Etapa | Status | Observação |
|-------|--------|------------|
| Register CUSTOMER | ✅ | userId: `7f50a202-4480-4363-a0ee-5f80112d461e` |
| Register MERCHANT_OWNER | ✅ | merchantId: `f9596c24-2822-4106-bd5a-57a400348562` |
| Confirm email CUSTOMER | ✅ | Token via `docker exec aom-redis` |
| Confirm email MERCHANT | ✅ | Token via `docker exec aom-redis` |
| Login | ✅ | JWT obtido |
| Create order | ✅ | orderId: `fed52996-2fdf-4460-917c-70bc50d32068`, 8990 cents |
| Card token (MP API) | ✅ | Token: `873afa89ee3c55c588460cf5d90f17f2` |
| Process payment | ✅ | **APPROVED** — mpPaymentId: `1347070305`, txnId: `txn_7dee2969`, 1925ms |
| **Resultado final** | ✅ | **Pagamento aprovado com MercadoPago Sandbox** |

### Troubleshooting Durante a Execução

| Problema | Causa | Solução |
|----------|-------|---------|
| `MP_GATEWAY_TIMEOUT` (503) | timeout default de 800ms insuficiente para MP Sandbox (~1900ms de latência) | Aumentado `MERCADOPAGO_TIMEOUT_MS` para 10000ms via `docker-compose.yml` |
| `CARD_DECLINED` (422) | Card token expirado (~2 min entre geração e uso) | Gerar novo card token imediatamente antes do pagamento |
