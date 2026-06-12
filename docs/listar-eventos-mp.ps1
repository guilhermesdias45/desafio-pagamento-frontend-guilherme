<# 
.SYNOPSIS
    Lista eventos de webhook do Mercado Pago enriquecidos com dados da API do MP.

.DESCRIPTION
    Consulta a API local do ngrok e a API do Mercado Pago para mostrar cada evento de webhook
    com: MP ID, Evento, Status Pagamento, HTTP Status, Data/Hora, Responsável, Comprador.

.PARAMETER Limite
    Número máximo de eventos de webhook a buscar no ngrok (padrão: 20).

.PARAMETER Token
    Access token do Mercado Pago (TEST ou PROD). Padrão: token TEST do projeto.

.EXAMPLE
    .\listar-eventos-mp.ps1

.EXAMPLE
    .\listar-eventos-mp.ps1 -Limite 50

.EXAMPLE
    .\listar-eventos-mp.ps1 -Token "APP_USR-xxxxx"
#>

param(
    [int]$Limite = 20,
    [string]$Token = "TEST-6025083406574896-060810-3f27c3e234e95149c11811b516be6ea2-567831283"
)

$ngrokApi = "http://127.0.0.1:4040/api/requests/http?limit=$Limite"
$mpApiBase = "https://api.mercadopago.com/v1/payments"

Write-Host "Consultando ngrok API: $ngrokApi" -ForegroundColor Cyan

if (!(Get-Command curl.exe -ErrorAction SilentlyContinue)) {
    Write-Host "ERRO: curl.exe não encontrado. Certifique-se de que está no PATH." -ForegroundColor Red
    exit 1
}

if (!(Test-NetConnection -ComputerName "127.0.0.1" -Port 4040 -InformationAction SilentlyContinue)) {
    Write-Host "ERRO: Ngrok não está rodando em localhost:4040. Execute 'ngrok http 8082' primeiro." -ForegroundColor Red
    exit 1
}

try {
    $raw = curl.exe -s $ngrokApi 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao chamar ngrok API (exit code $LASTEXITCODE). Ngrok está rodando?"
    }
    $json = $raw | ConvertFrom-Json -ErrorAction Stop
}
catch {
    Write-Error "Erro ao consultar ngrok: $_"
    Write-Host "`nDica: inicie o ngrok com: ngrok http 8082" -ForegroundColor Yellow
    exit 1
}

$webhookRequests = $json.requests | Where-Object { 
    $_.request.uri -like "*/api/v1/webhooks/mercadopago*" 
}

if (-not $webhookRequests -or $webhookRequests.Count -eq 0) {
    Write-Host "`nNenhum webhook do Mercado Pago encontrado nas últimas $Limite requisições." -ForegroundColor Yellow
    exit 0
}

$webhookEvents = $webhookRequests | ForEach-Object {
    $req = $_
    $uri = $req.request.uri
    $dataId = [regex]::Match($uri, 'data\.id=(\d+)').Groups[1].Value
    $start = $req.start
    $statusCode = $req.response.status_code

    $action = "desconhecido"
    $xRequestId = ""
    try {
        $rawB64 = $req.request.raw
        if ($rawB64) {
            $body = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($rawB64))
            $actionMatch = [regex]::Match($body, '"action":"([^"]+)"')
            if ($actionMatch.Success) {
                $action = $actionMatch.Groups[1].Value
            }
        }
    }
    catch { }

    [PSCustomObject]@{
        'MP ID'         = if ($dataId) { $dataId } else { "N/A" }
        'Evento'        = $action
        'HTTP'          = $statusCode
        'Data/Hora'     = $start
        'X-Request-ID'  = $xRequestId
    }
}

$uniqueIds = $webhookEvents | Where-Object { $_.'MP ID' -ne "N/A" } | Select-Object -ExpandProperty 'MP ID' -Unique

$paymentCache = @{}
if ($uniqueIds.Count -gt 0) {
    Write-Host "Buscando detalhes de $($uniqueIds.Count) pagamento(s) na API do MP..." -ForegroundColor Cyan
    foreach ($id in $uniqueIds) {
        try {
            $mpUrl = "$mpApiBase/$id"
            $mpRaw = curl.exe -s $mpUrl -H "Authorization: Bearer $Token" 2>&1
            if ($LASTEXITCODE -eq 0) {
                $mpJson = $mpRaw | ConvertFrom-Json -ErrorAction Stop
                $paymentCache[$id] = $mpJson
            } else {
                Write-Warning ("Falha ao buscar pagamento {0}: exit code $LASTEXITCODE" -f $id)
            }
        }
        catch {
            $errMsg = $_.ToString()
            Write-Warning ("Falha ao buscar pagamento {0}: {1}" -f $id, $errMsg)
        }
    }
}

$results = $webhookEvents | ForEach-Object {
    $evt = $_
    $id = $evt.'MP ID'
    $mpData = $paymentCache[$id]

    $statusPagamento = if ($mpData) { $mpData.status } else { "N/A" }
    $comprador = "N/A"
    if ($mpData -and $mpData.payer) {
        $name = "$($mpData.payer.first_name) $($mpData.payer.last_name)".Trim()
        $email = $mpData.payer.email
        $doc = if ($mpData.payer.identification) { $mpData.payer.identification.number } else { $null }
        $parts = @()
        if ($name) { $parts += $name }
        if ($email) { $parts += $email }
        if ($doc) { $parts += "CPF: $doc" }
        $comprador = $parts -join " | "
    }

    $responsavel = if ($evt.Evento -like "payment*") { "MercadoPago" } elseif ($evt.Evento -like "card*") { "MercadoPago (card)" } else { "Desconhecido" }
    if ($evt.'X-Request-ID') { $responsavel += " [$($evt.'X-Request-ID'.Substring(0,8))]" }

    [PSCustomObject]@{
        'MP ID'         = $id
        'Evento'        = $evt.Evento
        'Status'        = $statusPagamento
        'HTTP'          = $evt.HTTP
        'Data/Hora'     = $evt.'Data/Hora'
        'Responsável'   = $responsavel
        'Comprador'     = $comprador
    }
}

$results | Sort-Object { [DateTime]::Parse($_.'Data/Hora') } -Descending | Format-Table -AutoSize

Write-Host "`nTotal: $($results.Count) evento(s) exibido(s)" -ForegroundColor Cyan