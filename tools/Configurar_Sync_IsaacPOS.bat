@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0configurar_sync_gui.ps1"
