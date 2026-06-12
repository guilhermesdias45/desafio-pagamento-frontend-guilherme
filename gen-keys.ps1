# Compatibilidade com diferentes versões do .NET
$assemblies = [System.Reflection.Assembly]::LoadWithPartialName("System.Security")
if (-not $assemblies) {
    Write-Host "ERRO: Não foi possível carregar o assembly System.Security" -ForegroundColor Red
    exit 1
}

function New-DerLen([int]$n) {
    if ($n -lt 128)  { return [byte[]]@($n) }
    if ($n -le 255)  { return [byte[]]@(0x81, $n) }
    return [byte[]]@(0x82, ($n -shr 8), ($n -band 0xFF))
}
function New-DerInt([byte[]]$raw) {
    $i = 0
    while ($i -lt ($raw.Length - 1) -and $raw[$i] -eq 0) { $i++ }
    [byte[]]$v = $raw[$i..($raw.Length - 1)]
    if ($v[0] -band 0x80) { $v = [byte[]]([byte[]]@(0) + $v) }
    return [byte[]]([byte[]]@(0x02) + (New-DerLen $v.Length) + $v)
}
function New-DerSeq([byte[]]$body) {
    return [byte[]]([byte[]]@(0x30) + (New-DerLen $body.Length) + $body)
}

# Criar RSA com fallback para versões mais recentes do .NET
try {
    $rsa = New-Object System.Security.Cryptography.RSACryptoServiceProvider 2048
    $k = $rsa.ExportParameters($true)
} catch {
    try {
        # Tentar usar o RSA type class
        $rsaType = [System.Security.Cryptography.RSA]
        $rsa = $rsaType.Create(2048)
        $k = $rsa.ExportParameters($true)
    } catch {
        Write-Host "ERRO: Não foi possível criar RSA. Certifique-se de que o .NET Framework está instalado." -ForegroundColor Red
        exit 1
    }
}

[byte[]]$privDer = New-DerSeq ([byte[]](
    (New-DerInt @([byte]0)) +
    (New-DerInt $k.Modulus)   + (New-DerInt $k.Exponent) +
    (New-DerInt $k.D)         + (New-DerInt $k.P)        +
    (New-DerInt $k.Q)         + (New-DerInt $k.DP)       +
    (New-DerInt $k.DQ)        + (New-DerInt $k.InverseQ)
))

[byte[]]$pubSeq = New-DerSeq ([byte[]]((New-DerInt $k.Modulus) + (New-DerInt $k.Exponent)))
[byte[]]$oid    = @(0x06,0x09,0x2A,0x86,0x48,0x86,0xF7,0x0D,0x01,0x01,0x01,0x05,0x00)
[byte[]]$bits   = [byte[]](@(0x03) + (New-DerLen ($pubSeq.Length+1)) + @(0x00) + $pubSeq)
[byte[]]$pubDer = New-DerSeq ([byte[]]((New-DerSeq $oid) + $bits))

function To-EnvPem([byte[]]$der, [string]$label) {
    $body = [Convert]::ToBase64String($der) -replace '(.{64})', '$1\n'
    return "-----BEGIN $label-----\n${body}\n-----END $label-----"
}

$rng = [System.Security.Cryptography.RNGCryptoServiceProvider]::new()

$aes = New-Object byte[] 32; $rng.GetBytes($aes)
$aesHex = -join ($aes | ForEach-Object { $_.ToString('x2') })

$sec = New-Object byte[] 32; $rng.GetBytes($sec)
$secret = [Convert]::ToBase64String($sec) -replace '[+/=]',''

Write-Host ""
Write-Host "=== Cole estas linhas no .env ==="
Write-Host ""
Write-Host "JWT_PRIVATE_KEY=$(To-EnvPem $privDer 'RSA PRIVATE KEY')"
Write-Host ""
Write-Host "JWT_PUBLIC_KEY=$(To-EnvPem $pubDer 'PUBLIC KEY')"
Write-Host ""
Write-Host "TOTP_AES_KEY=$aesHex"
Write-Host ""
Write-Host "INTERNAL_SECRET=$secret"
Write-Host ""
