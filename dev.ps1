# Session-local Java setup; does not modify system settings or wrapper files.
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs = @('build'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$portableJdk = Get-ChildItem (Join-Path $projectRoot 'work/toolchains') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
$previousJavaHome = $env:JAVA_HOME
$previousGradleHome = $env:GRADLE_USER_HOME
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    if ($portableJdk) { $env:JAVA_HOME = $portableJdk.FullName }
    $env:GRADLE_USER_HOME = Join-Path $projectRoot 'work/gradle-home'
    # Windows AF_UNIX sockets fail with some long/extended temporary paths.
    # Keep this process-local and use a short directory in the Codex workspace.
    $socketTemp = Join-Path $env:USERPROFILE 'Documents/Codex/work/worldeater-tmp'
    New-Item -ItemType Directory -Force $socketTemp | Out-Null
    $env:JAVA_TOOL_OPTIONS = ($previousJavaOptions + ' "-Djdk.net.unixdomain.tmpdir=' + $socketTemp + '"').Trim()
    Push-Location $projectRoot
    try {
        & ./gradlew.bat @GradleArgs
        $result = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleHome
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
}
exit $result
