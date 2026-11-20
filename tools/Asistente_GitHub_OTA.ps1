<#
.SYNOPSIS
  Asistente visual de 1 clic para terminar de conectar GitHub Actions OTA:
  - Abre GitHub para crear el repo pedidos-proveedores-android
  - Copia cada uno de los 4 Secrets al portapapeles sin mostrar contraseñas
  - Ejecuta git push y publica el tag v1.0.0 para activar las futuras OTA
#>
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$signDir = "$env:USERPROFILE\PedidosProveedores-Signing"
$b64File = Join-Path $signDir "KEYSTORE_BASE64.txt"
$secretsFile = Join-Path $signDir "GITHUB_SECRETS_NO_SUBIR.txt"

$secrets = @{}
if (Test-Path $secretsFile) {
  foreach ($ln in Get-Content $secretsFile -Encoding UTF8) {
    if ($ln -match '^(KEY_ALIAS|KEYSTORE_PASSWORD|KEY_PASSWORD)=(.+)$') {
      $secrets[$matches[1]] = $matches[2]
    }
  }
}

$form = New-Object System.Windows.Forms.Form
$form.Text = "Activar Actualizaciones OTA en GitHub"
$form.Size = New-Object System.Drawing.Size(540, 510)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox = $false
$form.Font = New-Object System.Drawing.Font("Segoe UI", 9.5)

$y = 14
$lblTitle = New-Object System.Windows.Forms.Label
$lblTitle.Text = "Paso final OTA: GitHub Repo + 4 Secrets (copiado en 1 clic)"
$lblTitle.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Bold)
$lblTitle.Location = New-Object System.Drawing.Point(16, $y)
$lblTitle.Size = New-Object System.Drawing.Size(495, 24)
$form.Controls.Add($lblTitle)
$y += 34

$btnNewRepo = New-Object System.Windows.Forms.Button
$btnNewRepo.Text = "1. Crear repo público 'pedidos-proveedores-android' en GitHub"
$btnNewRepo.Location = New-Object System.Drawing.Point(16, $y)
$btnNewRepo.Size = New-Object System.Drawing.Size(495, 34)
$btnNewRepo.Add_Click({ Start-Process "https://github.com/new?name=pedidos-proveedores-android&visibility=public" })
$form.Controls.Add($btnNewRepo)
$y += 42

$btnOpenSecrets = New-Object System.Windows.Forms.Button
$btnOpenSecrets.Text = "2. Abrir página de Secrets de GitHub Actions (pegar los 4 abajo)"
$btnOpenSecrets.Location = New-Object System.Drawing.Point(16, $y)
$btnOpenSecrets.Size = New-Object System.Drawing.Size(495, 34)
$btnOpenSecrets.Add_Click({ Start-Process "https://github.com/mrivero105-oss/pedidos-proveedores-android/settings/secrets/actions" })
$form.Controls.Add($btnOpenSecrets)
$y += 44

$lblSec = New-Object System.Windows.Forms.Label
$lblSec.Text = "Pulsa cada botón para copiar su valor secreto directo al portapapeles:"
$lblSec.Location = New-Object System.Drawing.Point(16, $y)
$lblSec.Size = New-Object System.Drawing.Size(495, 20)
$form.Controls.Add($lblSec)
$y += 24

$txtStatus = New-Object System.Windows.Forms.TextBox
$txtStatus.Multiline = $true
$txtStatus.ReadOnly = $true
$txtStatus.ScrollBars = "Vertical"
$txtStatus.Font = New-Object System.Drawing.Font("Consolas", 9)

function Add-CopyButton([string]$label, [int]$bx, [int]$by, [int]$bw, [scriptblock]$action) {
  $b = New-Object System.Windows.Forms.Button
  $b.Text = $label
  $b.Location = New-Object System.Drawing.Point($bx, $by)
  $b.Size = New-Object System.Drawing.Size($bw, 32)
  $b.Add_Click($action)
  $form.Controls.Add($b)
}

Add-CopyButton "Copiar KEYSTORE_BASE64" 16 $y 242 {
  if (Test-Path $b64File) {
    Set-Clipboard -Value ([IO.File]::ReadAllText($b64File))
    $txtStatus.AppendText("Copiado KEYSTORE_BASE64 al portapapeles.`r`n")
  }
}
Add-CopyButton "Copiar KEYSTORE_PASSWORD" 269 $y 242 {
  if ($secrets.ContainsKey("KEYSTORE_PASSWORD")) {
    Set-Clipboard -Value $secrets["KEYSTORE_PASSWORD"]
    $txtStatus.AppendText("Copiado KEYSTORE_PASSWORD al portapapeles.`r`n")
  }
}
$y += 38

Add-CopyButton "Copiar KEY_ALIAS (pedidos-release)" 16 $y 242 {
  $aliasVal = if ($secrets.ContainsKey("KEY_ALIAS")) { $secrets["KEY_ALIAS"] } else { "pedidos-release" }
  Set-Clipboard -Value $aliasVal
  $txtStatus.AppendText("Copiado KEY_ALIAS ($aliasVal) al portapapeles.`r`n")
}
Add-CopyButton "Copiar KEY_PASSWORD" 269 $y 242 {
  if ($secrets.ContainsKey("KEY_PASSWORD")) {
    Set-Clipboard -Value $secrets["KEY_PASSWORD"]
    $txtStatus.AppendText("Copiado KEY_PASSWORD al portapapeles.`r`n")
  }
}
$y += 46

$btnPush = New-Object System.Windows.Forms.Button
$btnPush.Text = "3. Subir código a GitHub y publicar Release OTA v1.0.0"
$btnPush.BackColor = [System.Drawing.Color]::FromArgb(37, 99, 235)
$btnPush.ForeColor = [System.Drawing.Color]::White
$btnPush.FlatStyle = "Flat"
$btnPush.Font = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
$btnPush.Location = New-Object System.Drawing.Point(16, $y)
$btnPush.Size = New-Object System.Drawing.Size(495, 38)
$form.Controls.Add($btnPush)
$y += 48

$txtStatus.Location = New-Object System.Drawing.Point(16, $y)
$txtStatus.Size = New-Object System.Drawing.Size(495, 130)
$txtStatus.Text = "Keystore permanente listo en $signDir`r`nCuando hayas creado el repo y pegado los 4 Secrets, pulsa el botón azul."
$form.Controls.Add($txtStatus)

$btnPush.Add_Click({
  $btnPush.Enabled = $false
  $txtStatus.AppendText("`r`nSubiendo rama main y tag v1.0.0 a GitHub...`r`n")
  $form.Refresh()
  try {
    Push-Location $repoRoot
    $out1 = & git push -u origin main 2>&1 | Out-String
    $txtStatus.AppendText($out1.Trim() + "`r`n")
    if ($LASTEXITCODE -ne 0) {
      $txtStatus.AppendText("Error al subir main. Verifica que creaste el repo vacío e iniciaste sesión en GitHub.`r`n")
      return
    }
    & git tag -f v1.0.0 | Out-Null
    $out2 = & git push origin v1.0.0 2>&1 | Out-String
    $txtStatus.AppendText($out2.Trim() + "`r`n")
    if ($LASTEXITCODE -eq 0) {
      $txtStatus.AppendText("LISTO: GitHub Actions está compilando y publicando tu Release OTA v1.0.0.`r`n")
      Start-Process "https://github.com/mrivero105-oss/pedidos-proveedores-android/actions"
    }
  } finally {
    Pop-Location
    $btnPush.Enabled = $true
  }
})

[void]$form.ShowDialog()
