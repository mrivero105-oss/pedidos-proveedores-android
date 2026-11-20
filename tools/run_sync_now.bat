@echo off
setlocal
cd /d "%~dp0"
python sync_isaac_pos_to_supabase.py --push-rest
echo.
pause
