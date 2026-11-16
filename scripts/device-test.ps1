# 真机快速安装 + 冒烟验证脚本
#
# 用法（在工程根目录）：
#   .\scripts\device-test.ps1
#   .\scripts\device-test.ps1 -SkipInstall        # 只做验证
#
# 前提：手机已连接并授权 USB 调试（adb devices 显示 device）。

param(
    [string]$Adb = 'F:\DevelopmentTool\AndroidSDK\platform-tools\adb.exe',
    [string]$Apk = '',
    [switch]$SkipInstall
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($Apk)) {
    $Apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
}

function Assert-Device {
    $lines = & $Adb devices
    if (-not ($lines | Where-Object { $_ -match '\sdevice$' })) {
        throw "未检测到已授权设备，请检查 USB 连接和 USB 调试授权。当前：`n$($lines -join "`n")"
    }
}

function Invoke-Adb([string[]]$AdbArgs) {
    $out = & $Adb @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0) { Write-Host "  (adb 返回 ${LASTEXITCODE}: $($out -join ' '))" -ForegroundColor DarkYellow }
    return $out
}

function Get-UiXml {
    & $Adb shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
    return (& $Adb shell cat /sdcard/ui.xml)
}

Write-Host '== 1. 检查设备 ==' -ForegroundColor Cyan
Assert-Device
$model = (& $Adb shell getprop ro.product.model).Trim()
$release = (& $Adb shell getprop ro.build.version.release).Trim()
$sdk = (& $Adb shell getprop ro.build.version.sdk).Trim()
$rom = (& $Adb shell getprop ro.build.version.opporom).Trim()
Write-Host "  $model / Android $release (API $sdk) / ColorOS $rom"

if (-not $SkipInstall) {
    Write-Host '== 2. 安装 APK ==' -ForegroundColor Cyan
    if (-not (Test-Path $Apk)) { throw "APK 不存在：$Apk" }
    $install = & $Adb install -r $Apk 2>&1
    Write-Host "  $($install | Select-Object -Last 1)"
    if ($install -match 'Failure') { throw "安装失败：$install" }
}

Write-Host '== 3. 授权通知权限（Android 13+）==' -ForegroundColor Cyan
if ([int]$sdk -ge 33) {
    Invoke-Adb @('shell', 'pm', 'grant', 'com.chargetone.app', 'android.permission.POST_NOTIFICATIONS') | Out-Null
}

Write-Host '== 4. 启动应用 ==' -ForegroundColor Cyan
Invoke-Adb @('shell', 'am', 'start', '-n', 'com.chargetone.app/.ui.MainActivity') | Out-Null
Start-Sleep -Seconds 3

Write-Host '== 5. 打开「后台监听服务」开关 ==' -ForegroundColor Cyan
$xml = Get-UiXml
$switchNodes = [regex]::Matches($xml, '<node[^>]*checkable="true"[^>]*>')
if ($switchNodes.Count -eq 0) {
    Write-Host '  未找到开关节点，请手动点开「后台监听服务」开关' -ForegroundColor Yellow
} else {
    $bounds = [regex]::Match($switchNodes[0].Value, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    $x = [int](([int]$bounds.Groups[1].Value + [int]$bounds.Groups[3].Value) / 2)
    $y = [int](([int]$bounds.Groups[2].Value + [int]$bounds.Groups[4].Value) / 2)
    $checked = $switchNodes[0].Value -match 'checked="true"'
    if ($checked) {
        Write-Host '  服务开关已开启'
    } else {
        Write-Host "  点击开关 ($x,$y)"
        Invoke-Adb @('shell', 'input', 'tap', "$x", "$y") | Out-Null
        Start-Sleep -Seconds 3
    }
}

Write-Host '== 6. 校验前台服务与通知 ==' -ForegroundColor Cyan
$services = Invoke-Adb @('shell', 'dumpsys', 'activity', 'services', 'com.chargetone.app')
$foreground = $services | Select-String -Pattern 'isForeground=true'
if ($foreground) { Write-Host '  前台服务：运行中' -ForegroundColor Green }
else { Write-Host '  前台服务：未运行（请检查通知权限/电池策略）' -ForegroundColor Red }

$notif = Invoke-Adb @('shell', 'dumpsys', 'notification') | Select-String -Pattern 'id=1001'
if ($notif) { Write-Host '  常驻通知：已发布' -ForegroundColor Green }
else { Write-Host '  常驻通知：未找到' -ForegroundColor Yellow }

Write-Host '== 7. 最近日志 ==' -ForegroundColor Cyan
& $Adb logcat -d -s ChargeTone:I 2>&1 | Select-Object -Last 12

Write-Host ''
Write-Host '下一步：手动插拔充电器，观察日志与声音；或运行：' -ForegroundColor Cyan
Write-Host "  & '$Adb' logcat -s ChargeTone:I" -ForegroundColor DarkGray
