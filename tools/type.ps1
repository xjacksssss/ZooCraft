# Types a chat command into the Minecraft dev client and presses Enter, for scripted checks of single-player worlds
# (such as the ATM10 copy) where RCON isn't available. Usage: powershell -File tools/type.ps1 "/zoo debug census" [-Window N]
param([string]$Text, [int]$Window = 0)
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Focus {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
"@
$proc = Get-Process java, javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like "Minecraft*" -or $_.MainWindowTitle -like "All The Mods*" } | Sort-Object StartTime | Select-Object -Skip $Window -First 1
if (-not $proc) { Write-Error "No Minecraft window found"; exit 1 }
[Focus]::SetForegroundWindow($proc.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 400
# Open chat with "t", then type the text. SendKeys treats + ^ % ~ ( ) { } [ ] as special, so wrap each in braces.
[System.Windows.Forms.SendKeys]::SendWait("t")
Start-Sleep -Milliseconds 400
$escaped = ($Text.ToCharArray() | ForEach-Object { if ('+^%~(){}[]'.Contains([string]$_)) { "{$_}" } else { [string]$_ } }) -join ''
[System.Windows.Forms.SendKeys]::SendWait($escaped)
Start-Sleep -Milliseconds 200
[System.Windows.Forms.SendKeys]::SendWait("{ENTER}")
Write-Output "typed: $Text"
