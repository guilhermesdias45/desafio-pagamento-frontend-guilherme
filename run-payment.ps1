$ErrorActionPreference = "Stop"
$tmp = "$env:TEMP\pay"

# Verificar dependências
if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "ERRO: Docker não encontrado. Por favor, instale o Docker Desktop para Windows." -ForegroundColor Red
    exit 1
}

if (!(Get-Command curl.exe -ErrorAction SilentlyContinue)) {
    Write-Host "ERRO: curl.exe não encontrado. Certifique-se de que está no PATH." -ForegroundColor Red
    exit 1
}

# Verificar se Redis está rodando
if (!(docker ps --format "{{.Names}}" | Where-Object { $_ -eq "aom-redis" })) {
    Write-Host "AVISO: Redis aom-redis não está rodando. Tentando iniciar..." -ForegroundColor Yellow
    docker start aom-redis 2>$null
    Start-Sleep -Seconds 2
    if (!(docker ps --format "{{.Names}}" | Where-Object { $_ -eq "aom-redis" })) {
        Write-Host "ERRO: Não foi possível iniciar Redis. Por favor, execute 'docker compose up -d' primeiro." -ForegroundColor Red
        exit 1
    }
}

# Verificar se API Gateway está rodando
$apiHealth = curl.exe -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health
if ($apiHealth -ne "200") {
    Write-Host "AVISO: API Gateway não está rodando em localhost:8080 (status: $apiHealth)." -ForegroundColor Yellow
    Write-Host "Por favor, execute 'docker compose --profile app up -d' primeiro." -ForegroundColor Yellow
    exit 1
}

# Garantir que o diretório temporary exista
if (!(Test-Path $tmp)) {
    New-Item -ItemType Directory -Path $tmp | Out-Null
}

$suffix = Get-Date -Format "yyyyMMdd-HHmmss"
$emailCliente = "cliente.$suffix@teste.com"
$emailLojista = "lojista.$suffix@teste.com"

# ═══════════════════════════════════════════════════════════════════
# PASSO 1: REGISTRAR CUSTOMER
# ═══════════════════════════════════════════════════════════════════
Write-Host "=== PASSO 1: Registrar CUSTOMER ===" -ForegroundColor Cyan
$json = "{`"email`":`"$emailCliente`",`"password`":`"Str0ng!Pass`",`"fullName`":`"Cliente Pagamento`",`"role`":`"CUSTOMER`"}"
[System.IO.File]::WriteAllText("$tmp-customer.json", $json)
$response = curl.exe -s -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" -d "@$tmp-customer.json" -w "\n%{http_code}"
try {
    $responseData = $response -split "\n"
    $responseBody = $responseData[0..($responseData.Length - 2)]
    $httpCode = $responseData[-1]
    $customerId = ($responseBody -join "") | ConvertFrom-Json
    if (-not $customerId -or $httpCode -ne "200") { Write-Host "ERRO: HTTP $httpCode - $responseBody" -ForegroundColor Red; exit 1 }
    Write-Host "OK customerId=$customerId" -ForegroundColor Green
} catch {
    Write-Host "ERRO: Falha ao registrar customer: $_" -ForegroundColor Red
    exit 1
}

# ═══════════════════════════════════════════════════════════════════
# PASSO 2: REGISTRAR MERCHANT_OWNER
# ═══════════════════════════════════════════════════════════════════
Write-Host "=== PASSO 2: Registrar MERCHANT_OWNER ===" -ForegroundColor Cyan
$json = "{`"email`":`"$emailLojista`",`"password`":`"Str0ng!Pass`",`"fullName`":`"Lojista Pagamento`",`"role`":`"MERCHANT_OWNER`",`"companyName`":`"Minha Loja`",`"cnpj`":`"12345678000195`"}"
[System.IO.File]::WriteAllText("$tmp-merchant.json", $json)
$response = curl.exe -s -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" -d "@$tmp-merchant.json" -w "\n%{http_code}"
try {
    $responseData = $response -split "\n"
    $responseBody = $responseData[0..($responseData.Length - 2)]
    $httpCode = $responseData[-1]
    $merchantId = ($responseBody -join "") | ConvertFrom-Json
    if (-not $merchantId -or $httpCode -ne "200") { Write-Host "ERRO: HTTP $httpCode - $responseBody" -ForegroundColor Red; exit 1 }
    Write-Host "OK merchantId=$merchantId" -ForegroundColor Green
} catch {
    Write-Host "ERRO: Falha ao registrar merchant: $_" -ForegroundColor Red
    exit 1
}

# ═══════════════════════════════════════════════════════════════════
# PASSO 3: CONFIRMAR EMAILS
# ═══════════════════════════════════════════════════════════════════
Write-Host "=== PASSO 3: Confirmar emails ===" -ForegroundColor Cyan
try {
    $keys = docker exec aom-redis redis-cli -a redis_dev_pass KEYS "email_confirm:*"
    foreach ($key in $keys) {
        $token = $key -replace "email_confirm:", ""
        if ($token.Length -eq 36) {
            $json = "{`"token`":`"$token`"}"
            [System.IO.File]::WriteAllText("$tmp-confirm.json", $json)
            $result = curl.exe -s -X POST http://localhost:8080/api/v1/auth/confirm-email -H "Content-Type: application/json" -d "@$tmp-confirm.json" -w "\n%{http_code}"
            $responseData = $result -split "\n"
            $resultBody = $responseData[0..($responseData.Length - 2)]
            $httpCode = $responseData[-1]
            Write-Host "  Token $token => HTTP $httpCode - $resultBody" -ForegroundColor Yellow
        }
    }
} catch {
    Write-Host "AVISO: Não foi possível verificar tokens de email confirm: $_" -ForegroundColor Yellow
}

# ═══════════════════════════════════════════════════════════════════
# PASSO 4: LOGIN
# ═══════════════════════════════════════════════════════════════════
Write-Host "=== PASSO 4: Login ===" -ForegroundColor Cyan
$json = "{`"email`":`"$emailCliente`",`"password`":`"Str0ng!Pass`"}"
[System.IO.File]::WriteAllText("$tmp-login.json", $json)
$response = curl.exe -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "@$tmp-login.json" -w "\n%{http_code}"
try {
    $responseData = $response -split "\n"
    $responseBody = $responseData[0..($responseData.Length - 2)]
    $httpCode = $responseData[-1]
    $accessToken = ($responseBody -join "") | ConvertFrom-Json
    if (-not $accessToken -or $httpCode -ne "200") { Write-Host "ERRO: HTTP $httpCode - $responseBody" -ForegroundColor Red; exit 1 }
    Write-Host "OK token: $($accessToken.Substring(0,40))..." -ForegroundColor Green
} catch {
    Write-Host "ERRO: Falha ao fazer login: $_" -ForegroundColor Red
    exit 1
}

# ═══════════════════════════════════════════════════════════════════
# PASSO 5: CRIAR PEDIDO
# ═══════════════════════════════════════════════════════════════════
Write-Host "=== PASSO 5: Criar pedido ===" -ForegroundColor Cyan
$idempKey = [Guid]::NewGuid()
$json = "{`"merchantId`":`"$merchantId`",`"items`":[{`"productId`":`"prod_vestido_azul`",`"description`":`"Vestido Azul Floral Tam M`",`"quantity`":1,`"unitPriceInCents`":8990}]}"
[System.IO.File]::WriteAllText("$tmp-order.json", $json)
$response = curl.exe -s -X POST http://localhost:8080/api/v1/orders -H "Content-Type: application/json" -H "Authorization: Bearer $accessToken" -H "X-User-Id: $customerId" -H "X-User-Email: $emailCliente" -H "Idempotency-Key: $idempKey" -d "@$tmp-order.json" -w "\n%{http_code}"
try {
    $responseData = $response -split "\n"
    $responseBody = $responseData[0..($responseData.Length - 2)]
    $httpCode = $responseData[-1]
    $orderId = ($responseBody -join "") | ConvertFrom-Json
    if (-not $orderId -or $httpCode -ne "200") { Write-Host "ERRO: HTTP $httpCode - $responseBody" -ForegroundColor Red; exit 1 }
    Write-Host "OK orderId=$orderId" -ForegroundColor Green
} catch {
    Write-Host "ERRO: Falha ao criar pedido: $_" -ForegroundColor Red
    exit 1
}

# ═══════════════════════════════════════════════════════════════════
# PASSO 6: GERAR CARD TOKEN (MERCADO PAGO)
# ═══════════════════════════════════════════════════════════════════
Write-Host "=== PASSO 6: Card Token MP ===" -ForegroundColor Cyan
$env:MP_TOKEN = "TEST-6025083406574896-060810-3f27c3e234e95149c11811b516be6ea2-567831283"
[System.IO.File]::WriteAllText("$tmp-card.json", '{"card_number":"5031433215406351","expiration_month":12,"expiration_year":2030,"security_code":"123","cardholder":{"name":"APRO"}}')
$response = curl.exe -s -X POST https://api.mercadopago.com/v1/card_tokens -H "Content-Type: application/json" -H "Authorization: Bearer $env:MP_TOKEN" -d "@$tmp-card.json" -w "\n%{http_code}"
try {
    $responseData = $response -split "\n"
    $responseBody = $responseData[0..($responseData.Length - 2)]
    $httpCode = $responseData[-1]
    $cardToken = ($responseBody -join "") | ConvertFrom-Json
    if (-not $cardToken -or $httpCode -ne "200") { Write-Host "ERRO: HTTP $httpCode - $responseBody" -ForegroundColor Red; exit 1 }
    Write-Host "OK cardToken=$cardToken" -ForegroundColor Green
} catch {
    Write-Host "ERRO: Falha ao gerar card token: $_" -ForegroundColor Red
    exit 1
}

# ═══════════════════════════════════════════════════════════════════
# PASSO 7: PROCESSAR PAGAMENTO
# ═══════════════════════════════════════════════════════════════════
Write-Host "=== PASSO 7: Processar pagamento ===" -ForegroundColor Cyan
$idempKey = [Guid]::NewGuid()
$json = "{`"amountInCents`":8990,`"currency`":`"BRL`",`"customerId`":`"$customerId`",`"orderId`":`"$orderId`",`"cardToken`":`"$cardToken`",`"paymentMethodId`":`"master`",`"installments`":1,`"idempotencyKey`":`"$idempKey`"}"
[System.IO.File]::WriteAllText("$tmp-payment.json", $json)
$response = curl.exe -s -m 30 -X POST http://localhost:8080/api/v1/transactions -H "Content-Type: application/json" -H "Authorization: Bearer $accessToken" -H "X-User-Email: $emailCliente" -H "X-Merchant-Id: $merchantId" -H "X-Forwarded-For: 192.168.1.100" -d "@$tmp-payment.json" -w "\n%{http_code}"
try {
    $responseData = $response -split "\n"
    $responseBody = $responseData[0..($responseData.Length - 2)]
    $httpCode = $responseData[-1]
    Write-Host "=== RESULTADO PAGAMENTO ===" -ForegroundColor Cyan
    ($responseBody -join "") | ConvertFrom-Json | ConvertTo-Json -Depth 10
    if ($httpCode -ne "200") { Write-Host "ERRO: HTTP $httpCode" -ForegroundColor Red }
} catch {
    Write-Host "ERRO: Falha ao processar pagamento: $_" -ForegroundColor Red
}

# ═══════════════════════════════════════════════════════════════════
# LIMPEZA DE ARQUIVOS TEMPORÁRIOS
# ═══════════════════════════════════════════════════════════════════
Write-Host "=== LIMPEZA ===" -ForegroundColor Cyan
try {
    foreach ($file in @("$tmp-customer.json", "$tmp-merchant.json", "$tmp-confirm.json", "$tmp-login.json", "$tmp-order.json", "$tmp-card.json", "$tmp-payment.json")) {
        if (Test-Path $file) { Remove-Item $file -Force }
    }
    Write-Host "OK arquivos temporários limpos" -ForegroundColor Green
} catch {
    Write-Host "AVISO: Não foi possível limpar arquivos temporários: $_" -ForegroundColor Yellow
}


