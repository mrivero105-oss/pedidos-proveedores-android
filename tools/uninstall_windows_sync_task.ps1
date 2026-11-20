param(
  [string]$TaskName = "IsaacPOS_Sync_Supabase"
)

$ErrorActionPreference = "Stop"
if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
  Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
  Write-Host "Tarea programada '$TaskName' eliminada." -ForegroundColor Yellow
} else {
  Write-Host "La tarea programada '$TaskName' no estaba registrada."
}
