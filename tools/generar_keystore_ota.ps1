<#
.SYNOPSIS
  Genera un keystore release fijo FUERA del repositorio para firmar APKs OTA
  (garantiza que Android actualice la app encima sin desinstalar ni perder datos).
  Imprime el valor Base64 para copiarlo en GitHub Secrets (KEYSTORE_BASE64).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tools\generar_keystore_ota.ps1
#>
param(
  [string]$OutputDir = "$env:USERPROFILE\PedidosProveedores-Signing",
  [string]$Alias = "pedidos-release"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
$jksPath = Join-Path $OutputDir "release.jks"

if (Test-Path $jksPath) {
  Write-Warning "Ya existe un keystore en $jksPath. No se sobrescribirá para proteger tu firma OTA."
} else {
  $keytool = Get-Command keytool.exe -ErrorAction SilentlyContinue
  if (-not $keytool) {
    $candidates = @(
      "$env:JAVA_HOME\bin\keytool.exe",
      "C:\Program Files\Eclipse Adoptium\jdk-17*\bin\keytool.exe",
      "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
    )
    foreach ($pat in $candidates) {
      $found = Get-Item $pat -ErrorAction SilentlyContinue | Select-Object -First 1
      if ($found) { $keytool = $found; break }
    }
  }
  if (-not $keytool) {
    throw "No se encontró keytool.exe. Asegúrate de tener Java 17 (JDK) en PATH o JAVA_HOME."
  }

  $keytoolExe = if ($keytool.PSObject.Properties.Name -contains "Source") { $keytool.Source } else { $keytool.FullName }
  Write-Host "Creando keystore release en: $jksPath" -ForegroundColor Cyan
  Write-Host "Anota la contraseña que elijas; la necesitarás en GitHub Secrets." -ForegroundColor Yellow
  & $keytoolExe -genkeypair -v -keystore $jksPath -keyalg RSA -keysize 2048 -validity 10000 -alias $Alias
  if ($LASTEXITCODE -ne 0) {
    throw "Error al generar el keystore con keytool."
  }
}

$b64Path = Join-Path $OutputDir "KEYSTORE_BASE64.txt"
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($jksPath))
Set-Content -Path $b64Path -Value $b64 -Encoding Ascii -NoNewline

Write-Host ""
Write-Host "Keystore listo (FUERA del repositorio):" -ForegroundColor Green
Write-Host "  Archivo JKS : $jksPath"
Write-Host "  Base64 listo: $b64Path"
Write-Host ""
Write-Host "Configura estos 4 Secrets en GitHub -> Settings -> Secrets and variables -> Actions:" -ForegroundColor Cyan
Write-Host "  1. KEYSTORE_BASE64   = contenido completo de $b64Path"
Write-Host "  2. KEYSTORE_PASSWORD = contraseña del keystore"
Write-Host "  3. KEY_ALIAS         = $Alias"
Write-Host "  4. KEY_PASSWORD      = contraseña de la clave (la misma del keystore)"
