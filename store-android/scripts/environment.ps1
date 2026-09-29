$ErrorActionPreference = 'Stop'
$androidProjectRoot = Split-Path $PSScriptRoot -Parent
# Override JAVA_HOME before sourcing only when using the documented JDK 21 toolchain.
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21' }
$env:GRADLE_USER_HOME = Join-Path $androidProjectRoot '.gradle-home'
$env:ANDROID_HOME = Join-Path $androidProjectRoot '.tools/sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $androidProjectRoot '.android-home'
$env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
$env:DEBUG = ''
