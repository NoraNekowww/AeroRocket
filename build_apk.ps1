$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

$tool = 'E:\Alpha_nz_Files\_toolchain'
$env:JAVA_HOME        = "$tool\jdk"
$env:ANDROID_HOME     = "$tool\android-sdk"
$env:ANDROID_SDK_ROOT = "$tool\android-sdk"
$env:PATH             = "$tool\jdk\bin;$tool\android-sdk\platform-tools;$env:PATH"
$env:GRADLE_OPTS      = "-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US"

$proj = 'E:\Alpha_nz_Files\WaterRocket'
Set-Location $proj

Write-Output "JAVA_HOME = $env:JAVA_HOME"
Write-Output "ANDROID_HOME = $env:ANDROID_HOME"

$target = if ($args.Count -gt 0) { $args[0] } else { 'assembleRelease' }
Write-Output "Target: $target"

& "$tool\gradle-8.7\bin\gradle.bat" `
    --console=plain `
    --stacktrace `
    -p $proj `
    $target 2>&1 | Out-File -Encoding utf8 "$proj\build.log"

Write-Output "GRADLE_EXIT=$LASTEXITCODE"
