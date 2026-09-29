# Disposable local DEBUG identity only. Never use for release signing.
. "$PSScriptRoot/environment.ps1"
$debugKeyPath = Join-Path $androidProjectRoot '.android-home/debug.keystore'
if (-not (Test-Path -LiteralPath $debugKeyPath)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $debugKeyPath -Parent) | Out-Null
    & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore $debugKeyPath -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Local debug key generation failed' }
}
