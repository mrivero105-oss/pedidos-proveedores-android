<#
.SYNOPSIS
  Asistente visual (sin consola ni Bloc de notas) para configurar tools\.env,
  probar la subida real a Supabase y activar la Tarea Programada de Windows cada 15 min.
  Las credenciales se guardan únicamente en tools\.env (excluido en .gitignore).
#>
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

$toolsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$envPath = Join-Path $toolsDir ".env"
$syncScript = Join-Path $toolsDir "sync_isaac_pos_to_supabase.py"
$installTaskScript = Join-Path $toolsDir "install_windows_sync_task.ps1"
$supabaseApiKeysUrl = "https://supabase.com/dashboard/project/puvqiipussyktfmgafgv/settings/api-keys"

# Leer valores actuales si ya existe tools/.env
$existing = @{}
if (Test-Path $envPath) {
  foreach ($line in Get-Content $envPath -Encoding UTF8) {
    $trimmed = $line.Trim()
    if ($trimmed -and -not $trimmed.StartsWith("#") -and $trimmed.Contains("=")) {
      $parts = $trimmed.Split("=", 2)
      $existing[$parts[0].Trim()] = $parts[1].Trim()
    }
  }
}

$form = New-Object System.Windows.Forms.Form
$form.Text = "Configurar Sync Automático Isaac POS -> Supabase"
$form.Size = New-Object System.Drawing.Size(560, 520)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox = $false
$form.Font = New-Object System.Drawing.Font("Segoe UI", 9.5)

$y = 14

$lblTitle = New-Object System.Windows.Forms.Label
$lblTitle.Text = "Sincronización automática cada 15 min (solo se guarda en tu PC)"
$lblTitle.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Bold)
$lblTitle.Location = New-Object System.Drawing.Point(16, $y)
$lblTitle.Size = New-Object System.Drawing.Size(510, 24)
$form.Controls.Add($lblTitle)
$y += 32

$btnOpenKeys = New-Object System.Windows.Forms.Button
$btnOpenKeys.Text = "1. Abrir API Keys de Supabase en el navegador"
$btnOpenKeys.Location = New-Object System.Drawing.Point(16, $y)
$btnOpenKeys.Size = New-Object System.Drawing.Size(510, 32)
$btnOpenKeys.Add_Click({ Start-Process $supabaseApiKeysUrl })
$form.Controls.Add($btnOpenKeys)
$y += 42

$lblKey = New-Object System.Windows.Forms.Label
$lblKey.Text = "2. Pega tu Secret key de Supabase (sb_secret_... o service_role eyJ...):"
$lblKey.Location = New-Object System.Drawing.Point(16, $y)
$lblKey.Size = New-Object System.Drawing.Size(510, 20)
$form.Controls.Add($lblKey)
$y += 22

$txtKey = New-Object System.Windows.Forms.TextBox
$txtKey.Location = New-Object System.Drawing.Point(16, $y)
$txtKey.Size = New-Object System.Drawing.Size(420, 26)
$txtKey.UseSystemPasswordChar = $true
if ($existing.ContainsKey("SUPABASE_SERVICE_ROLE_KEY") -and -not $existing["SUPABASE_SERVICE_ROLE_KEY"].StartsWith("PEGA_AQUI")) {
  $txtKey.Text = $existing["SUPABASE_SERVICE_ROLE_KEY"]
}
$form.Controls.Add($txtKey)

$chkShowKey = New-Object System.Windows.Forms.CheckBox
$chkShowKey.Text = "Ver"
$chkShowKey.Location = New-Object System.Drawing.Point(444, ($y + 2))
$chkShowKey.Size = New-Object System.Drawing.Size(75, 22)
$chkShowKey.Add_CheckedChanged({ $txtKey.UseSystemPasswordChar = -not $chkShowKey.Checked })
$form.Controls.Add($chkShowKey)
$y += 38

$lblPgHeader = New-Object System.Windows.Forms.Label
$lblPgHeader.Text = "3. PostgreSQL 16 local (OPCIONAL - si lo dejas vacío usa C:\IsaacPOS_Respaldos):"
$lblPgHeader.Font = New-Object System.Drawing.Font("Segoe UI", 9.5, [System.Drawing.FontStyle]::Bold)
$lblPgHeader.Location = New-Object System.Drawing.Point(16, $y)
$lblPgHeader.Size = New-Object System.Drawing.Size(510, 20)
$form.Controls.Add($lblPgHeader)
$y += 24

$lblDb = New-Object System.Windows.Forms.Label
$lblDb.Text = "Nombre BD local:"
$lblDb.Location = New-Object System.Drawing.Point(16, $y)
$lblDb.Size = New-Object System.Drawing.Size(120, 22)
$form.Controls.Add($lblDb)

$txtDb = New-Object System.Windows.Forms.TextBox
$txtDb.Location = New-Object System.Drawing.Point(140, $y)
$txtDb.Size = New-Object System.Drawing.Size(160, 25)
if ($existing.ContainsKey("LOCAL_PG_DB")) { $txtDb.Text = $existing["LOCAL_PG_DB"] }
$form.Controls.Add($txtDb)

$lblUser = New-Object System.Windows.Forms.Label
$lblUser.Text = "Usuario:"
$lblUser.Location = New-Object System.Drawing.Point(312, $y)
$lblUser.Size = New-Object System.Drawing.Size(60, 22)
$form.Controls.Add($lblUser)

$txtUser = New-Object System.Windows.Forms.TextBox
$txtUser.Location = New-Object System.Drawing.Point(376, $y)
$txtUser.Size = New-Object System.Drawing.Size(150, 25)
$txtUser.Text = if ($existing.ContainsKey("LOCAL_PG_USER") -and $existing["LOCAL_PG_USER"]) { $existing["LOCAL_PG_USER"] } else { "postgres" }
$form.Controls.Add($txtUser)
$y += 32

$lblPass = New-Object System.Windows.Forms.Label
$lblPass.Text = "Contraseña PG:"
$lblPass.Location = New-Object System.Drawing.Point(16, $y)
$lblPass.Size = New-Object System.Drawing.Size(120, 22)
$form.Controls.Add($lblPass)

$txtPass = New-Object System.Windows.Forms.TextBox
$txtPass.Location = New-Object System.Drawing.Point(140, $y)
$txtPass.Size = New-Object System.Drawing.Size(296, 25)
$txtPass.UseSystemPasswordChar = $true
if ($existing.ContainsKey("LOCAL_PG_PASSWORD")) { $txtPass.Text = $existing["LOCAL_PG_PASSWORD"] }
$form.Controls.Add($txtPass)
$y += 38

$btnRun = New-Object System.Windows.Forms.Button
$btnRun.Text = "Guardar, Probar Subida y Activar Tarea (cada 15 min)"
$btnRun.BackColor = [System.Drawing.Color]::FromArgb(22, 163, 74)
$btnRun.ForeColor = [System.Drawing.Color]::White
$btnRun.FlatStyle = "Flat"
$btnRun.Font = New-Object System.Drawing.Font("Segoe UI", 10, [System.Drawing.FontStyle]::Bold)
$btnRun.Location = New-Object System.Drawing.Point(16, $y)
$btnRun.Size = New-Object System.Drawing.Size(510, 38)
$form.Controls.Add($btnRun)
$y += 48

$txtLog = New-Object System.Windows.Forms.TextBox
$txtLog.Multiline = $true
$txtLog.ReadOnly = $true
$txtLog.ScrollBars = "Vertical"
$txtLog.Font = New-Object System.Drawing.Font("Consolas", 9)
$txtLog.Location = New-Object System.Drawing.Point(16, $y)
$txtLog.Size = New-Object System.Drawing.Size(510, 165)
$txtLog.Text = "Listo. Pega tu Secret key de Supabase arriba y pulsa el botón verde."
$form.Controls.Add($txtLog)

$btnRun.Add_Click({
  $keyVal = $txtKey.Text.Trim()
  if (-not $keyVal -or $keyVal.StartsWith("PEGA_AQUI")) {
    [System.Windows.Forms.MessageBox]::Show("Debes pegar tu Secret key de Supabase (sb_secret_... o eyJ...).", "Falta clave", "OK", "Warning") | Out-Null
    return
  }

  $btnRun.Enabled = $false
  $txtLog.Text = "1/3 Guardando tools\.env localmente..." + "`r`n"
  $form.Refresh()

  $envLines = @(
    "# Generado localmente por configurar_sync_gui.ps1 (excluido en .gitignore)",
    "SUPABASE_URL=https://puvqiipussyktfmgafgv.supabase.co",
    "SUPABASE_SERVICE_ROLE_KEY=$keyVal",
    "LOCAL_PG_HOST=127.0.0.1",
    "LOCAL_PG_PORT=5432",
    "LOCAL_PG_DB=$($txtDb.Text.Trim())",
    "LOCAL_PG_USER=$($txtUser.Text.Trim())",
    "LOCAL_PG_PASSWORD=$($txtPass.Text)",
    "LOCAL_PG_PSQL=C:\Program Files\PostgreSQL\16\bin\psql.exe",
    "ISAAC_POS_BACKUP_DIR=C:\IsaacPOS_Respaldos",
    "ISAAC_POS_COMPANY_ID=2"
  )
  [IO.File]::WriteAllLines($envPath, $envLines, (New-Object System.Text.UTF8Encoding($false)))

  $txtLog.AppendText("2/3 Probando sincronización real hacia Supabase..." + "`r`n")
  $form.Refresh()

  try {
    $syncOutput = & python.exe $syncScript --push-rest 2>&1 | Out-String
    $syncCode = $LASTEXITCODE
    $txtLog.AppendText($syncOutput.Trim() + "`r`n")
    $form.Refresh()

    if ($syncCode -ne 0) {
      $txtLog.AppendText("ERROR en la subida (código $syncCode). Revisa la clave Secret y vuelve a intentar." + "`r`n")
      $btnRun.Enabled = $true
      return
    }

    $txtLog.AppendText("3/3 Registrando Tarea Programada de Windows (cada 15 min)..." + "`r`n")
    $form.Refresh()

    $taskOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installTaskScript -IntervalMinutes 15 2>&1 | Out-String
    $taskCode = $LASTEXITCODE
    $txtLog.AppendText($taskOutput.Trim() + "`r`n")

    if ($taskCode -eq 0) {
      $txtLog.AppendText("COMPLETADO: Sincronización automática activa cada 15 minutos." + "`r`n")
      [System.Windows.Forms.MessageBox]::Show("Sincronización probada y activada cada 15 minutos correctamente.", "Listo", "OK", "Information") | Out-Null
    } else {
      $txtLog.AppendText("Aviso: la subida funcionó, pero la tarea programada devolvió código $taskCode." + "`r`n")
    }
  } catch {
    $txtLog.AppendText("ERROR: $($_.Exception.Message)" + "`r`n")
  } finally {
    $btnRun.Enabled = $true
  }
})

[void]$form.ShowDialog()
