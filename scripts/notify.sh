#!/usr/bin/env bash
# Notification hook: Claude가 입력·승인을 기다릴 때 Windows 알림을 띄운다(실패해도 작업에는 영향 없음).
cat >/dev/null
msg="${1:-Claude가 확인을 기다리고 있어요}"
if command -v powershell.exe >/dev/null 2>&1; then
  powershell.exe -NoProfile -Command "Add-Type -AssemblyName System.Windows.Forms; \$n=New-Object System.Windows.Forms.NotifyIcon; \$n.Icon=[System.Drawing.SystemIcons]::Information; \$n.Visible=\$true; \$n.ShowBalloonTip(5000,'벌써도착 · Claude','$msg',[System.Windows.Forms.ToolTipIcon]::Info); Start-Sleep 6; \$n.Dispose()" >/dev/null 2>&1 &
elif command -v osascript >/dev/null 2>&1; then
  osascript -e "display notification \"$msg\" with title \"벌써도착 · Claude\"" >/dev/null 2>&1
fi
exit 0
