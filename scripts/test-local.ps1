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
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\ShootingModeMap.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\OneUiAssistantGuide.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\CameraRecoveryEngine.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\SensorHealth.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\DiscoveredSensor.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\RescueActionSpec.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\SensorSelectionState.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\DynamicUiBuilder.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\CameraViewerMath.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\VideoRecordingState.java"),
    (Join-Path $ProjectRoot "app\src\main\java\com\s23plus\camera\DiagnosticProfileCodec.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\CameraConfigTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\SamsungModelDatabaseTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\ShootingModeMapTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\OneUiAssistantGuideTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\CameraRecoveryEngineTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\SensorSelectionStateTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\DynamicUiBuilderTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\CameraViewerMathTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\VideoRecordingStateTest.java"),
    (Join-Path $ProjectRoot "app\src\test\java\com\s23plus\camera\DiagnosticProfileCodecTest.java")
)

& $Javac -encoding UTF-8 -d $OutDir $PureSources
if ($LASTEXITCODE -ne 0) { throw "Fallo compilando pruebas unitarias" }

Write-Host "Ejecutando CameraConfigTest..."
& $Java -cp $OutDir com.s23plus.camera.CameraConfigTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando CameraConfigTest" }

Write-Host "Ejecutando SamsungModelDatabaseTest..."
& $Java -cp $OutDir com.s23plus.camera.SamsungModelDatabaseTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando SamsungModelDatabaseTest" }

Write-Host "Ejecutando ShootingModeMapTest..."
& $Java -cp $OutDir com.s23plus.camera.ShootingModeMapTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando ShootingModeMapTest" }

Write-Host "Ejecutando OneUiAssistantGuideTest..."
& $Java -cp $OutDir com.s23plus.camera.OneUiAssistantGuideTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando OneUiAssistantGuideTest" }

Write-Host "Ejecutando CameraRecoveryEngineTest..."
& $Java -cp $OutDir com.s23plus.camera.CameraRecoveryEngineTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando CameraRecoveryEngineTest" }

Write-Host "Ejecutando SensorSelectionStateTest..."
& $Java -cp $OutDir com.s23plus.camera.SensorSelectionStateTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando SensorSelectionStateTest" }

Write-Host "Ejecutando DynamicUiBuilderTest..."
& $Java -cp $OutDir com.s23plus.camera.DynamicUiBuilderTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando DynamicUiBuilderTest" }

Write-Host "Ejecutando CameraViewerMathTest..."
& $Java -cp $OutDir com.s23plus.camera.CameraViewerMathTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando CameraViewerMathTest" }

Write-Host "Ejecutando VideoRecordingStateTest..."
& $Java -cp $OutDir com.s23plus.camera.VideoRecordingStateTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando VideoRecordingStateTest" }

Write-Host "Ejecutando DiagnosticProfileCodecTest..."
& $Java -cp $OutDir com.s23plus.camera.DiagnosticProfileCodecTest
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando DiagnosticProfileCodecTest" }
