# Clicks inside the Minecraft dev client window. Usage: powershell -File tools/click.ps1 <x> <y> [-Shift]
# x and y are pixels from the window's top-left corner, as in a tools/screenshot.ps1 capture.
param([int]$X, [int]$Y, [switch]$Shift)
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Mouse {
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(int flags, int dx, int dy, int data, IntPtr extra);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, int flags, IntPtr extra);
  public struct RECT { public int Left, Top, Right, Bottom; }
}
"@
$proc = Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like "Minecraft*" } | Select-Object -First 1
if (-not $proc) { Write-Error "No Minecraft window found"; exit 1 }
[Mouse]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 300
$r = New-Object Mouse+RECT
[Mouse]::GetWindowRect($proc.MainWindowHandle, [ref]$r) | Out-Null
[Mouse]::SetCursorPos($r.Left + $X, $r.Top + $Y) | Out-Null
Start-Sleep -Milliseconds 150
if ($Shift) { [Mouse]::keybd_event(0x10, 0, 0, [IntPtr]::Zero) }
[Mouse]::mouse_event(0x0002, 0, 0, 0, [IntPtr]::Zero)
Start-Sleep -Milliseconds 60
[Mouse]::mouse_event(0x0004, 0, 0, 0, [IntPtr]::Zero)
if ($Shift) { [Mouse]::keybd_event(0x10, 0, 2, [IntPtr]::Zero) }
Write-Output "clicked ($X, $Y)"
