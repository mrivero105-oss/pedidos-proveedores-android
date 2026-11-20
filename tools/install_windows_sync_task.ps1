<#
.SYNOPSIS
  Registra la Tarea Programada de Windows "IsaacPOS_Sync_Supabase" para mantener
  sincronizado el stock real y los proveedores de Isaac POS hacia Supabase cada N minutos.
  Funciona sin requerir permisos de Administrador (ejecuta bajo el usuario actual).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tools\install_windows_sync_task.ps1 -IntervalMinutes 15
#>
param(
  [int]$IntervalMinutes = 15,
  [string]$TaskName = "IsaacPOS_Sync_Supabase"
)

$ErrorActionPreference = "Stop"
$toolsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptPath = Join-Path $toolsDir "sync_isaac_pos_to_supabase.py"
$envPath = Join-Path $toolsDir ".env"

if (-not (Test-Path $scriptPath)) {
  throw "No se encontró el script sincronizador en: $scriptPath"
}

if (-not (Test-Path $envPath)) {
  Write-Warning "Aún no existe $envPath. Crea tools\.env con SUPABASE_SERVICE_ROLE_KEY antes de esperar subidas exitosas."
}

$pythonCmd = (Get-Command pythonw.exe -ErrorAction SilentlyContinue)
if (-not $pythonCmd) {
  $pythonCmd = (Get-Command python.exe -ErrorAction Stop)
}

$registered = $false
try {
  $action = New-ScheduledTaskAction `
    -Execute $pythonCmd.Source `
    -Argument "`"$scriptPath`" --push-rest" `
    -WorkingDirectory $toolsDir

  $trigger = New-ScheduledTaskTrigger `
    -Once `
    -At (Get-Date).AddMinutes(1) `
    -RepetitionInterval (New-TimeSpan -Minutes $IntervalMinutes) `
    -RepetitionDuration (New-TimeSpan -Days 3650)

  $settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 5)

  $principal = New-ScheduledTaskPrincipal `
    -UserId "$env:USERDOMAIN\$env:USERNAME" `
    -LogonType Interactive `
    -RunLevel Limited

  Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Description "Sincroniza en solo lectura el stock real y proveedores de Isaac POS hacia Supabase cada $IntervalMinutes minutos." `
    -Force | Out-Null
  $registered = $true
} catch {
  Write-Warning "Register-ScheduledTask devolvió '$($_.Exception.Message)'. Usando schtasks.exe como alternativa..."
}

if (-not $registered) {
  $taskCmd = "`"$($pythonCmd.Source)`" `"$scriptPath`" --push-rest"
  & schtasks.exe /Create /F /SC MINUTE /MO $IntervalMinutes /TN $TaskName /TR $taskCmd | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw "No se pudo registrar la tarea programada con schtasks.exe (código $LASTEXITCODE)."
  }
}

Write-Host "Tarea programada '$TaskName' registrada correctamente (cada $IntervalMinutes min)." -ForegroundColor Green
Write-Host "Ejecutable: $($pythonCmd.Source)"
Write-Host "Log de ejecuciones: $(Join-Path $toolsDir 'logs\sync.log')"
