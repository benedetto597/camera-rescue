$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ToolsRoot = "C:\Users\bened\.gemini\antigravity\brain\91cc6e57-4416-4b56-a368-6fa258b5dd9b\scratch\tools"
$Javac = Join-Path $ToolsRoot "jdk\jdk-17.0.21.0.2+1\bin\javac.exe"
$Java = Join-Path $ToolsRoot "jdk\jdk-17.0.21.0.2+1\bin\java.exe"
$AndroidJar = Join-Path $ToolsRoot "sdk\platforms\android-34\android.jar"

if (-not (Test-Path $Javac)) {
    $Javac = "javac"
    $Java = "java"
}

$OutDir = Join-Path $ProjectRoot "build\test-classes"
if (Test-Path $OutDir) {
    Remove-Item -Recurse -Force $OutDir
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$PureSources = @(
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\CameraConfig.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\SamsungModelDatabase.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\SensorHealth.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\DiscoveredSensor.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\RescueActionSpec.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\SensorSelectionState.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\CameraConfigTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\SamsungModelDatabaseTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\SensorSelectionStateTest.java")
)

& $Javac -encoding UTF-8 -d $OutDir $PureSources
if ($LASTEXITCODE -ne 0) { throw "Fallo compilando pruebas unitarias" }

Write-Host "Ejecutando CameraConfigTest..."
& $Java -cp $OutDir com.s23plus.camera.CameraConfigTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando CameraConfigTest" }

Write-Host "Ejecutando SamsungModelDatabaseTest..."
& $Java -cp $OutDir com.s23plus.camera.SamsungModelDatabaseTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando SamsungModelDatabaseTest" }

Write-Host "Ejecutando SensorSelectionStateTest..."
& $Java -cp $OutDir com.s23plus.camera.SensorSelectionStateTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando SensorSelectionStateTest" }
