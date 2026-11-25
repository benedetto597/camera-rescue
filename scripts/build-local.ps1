$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ToolsRoot = "C:\Users\bened\.gemini\antigravity\brain\91cc6e57-4416-4b56-a368-6fa258b5dd9b\scratch\tools"

function Resolve-JdkTool {
    param(
        [Parameter(Mandatory = $true)][string]$ToolName
    )

    $CandidateDirs = @()
    $ScratchJdkBin = Join-Path $ToolsRoot "jdk\jdk-17.0.21.0.2+1\bin"
    if (Test-Path $ScratchJdkBin) {
        $CandidateDirs += $ScratchJdkBin
    }
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $JavaHomeBin = Join-Path $env:JAVA_HOME "bin"
        if (Test-Path $JavaHomeBin) {
            $CandidateDirs += $JavaHomeBin
        }
    }

    foreach ($Dir in $CandidateDirs) {
        $Candidate = Join-Path $Dir "$ToolName.exe"
        if (Test-Path $Candidate) {
            return $Candidate
        }
    }

    $FromPath = Get-Command $ToolName -ErrorAction SilentlyContinue
    if ($null -ne $FromPath) {
        return $FromPath.Source
    }

    throw "No se encontro herramienta JDK '$ToolName'. Configura JAVA_HOME (JDK 17) o agrega '$ToolName' al PATH."
}

$Javac = Resolve-JdkTool -ToolName "javac"
$Java = Resolve-JdkTool -ToolName "java"
$Keytool = Resolve-JdkTool -ToolName "keytool"

$SdkRoots = @()
foreach ($CandidateSdk in @($env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA "Android\Sdk"), (Join-Path $ToolsRoot "sdk"))) {
    if (-not [string]::IsNullOrWhiteSpace($CandidateSdk) -and (Test-Path $CandidateSdk) -and ($SdkRoots -notcontains $CandidateSdk)) {
        $SdkRoots += $CandidateSdk
    }
}

if ($SdkRoots.Count -eq 0) {
    throw "No se encontro Android SDK en ANDROID_HOME, LOCALAPPDATA\Android\Sdk ni $ToolsRoot\sdk."
}

$BuildToolsDir = $null
foreach ($BtVersion in @("36.0.0", "35.0.0", "34.0.0", "android-14")) {
    foreach ($SdkRoot in $SdkRoots) {
        $CandidateBt = Join-Path $SdkRoot "build-tools\$BtVersion"
        if ((Test-Path (Join-Path $CandidateBt "aapt2.exe")) -and
            (Test-Path (Join-Path $CandidateBt "zipalign.exe")) -and
            (Test-Path (Join-Path $CandidateBt "lib\d8.jar")) -and
            (Test-Path (Join-Path $CandidateBt "lib\apksigner.jar"))) {
            $BuildToolsDir = $CandidateBt
            break
        }
    }
    if ($null -ne $BuildToolsDir) { break }
}

if ($null -eq $BuildToolsDir) {
    throw "No se encontro Android build-tools (36.0.0 / 35.0.0 / 34.0.0 / android-14) en: $($SdkRoots -join ', ')"
}

$AndroidJar = $null
foreach ($PlatformName in @("android-36", "android-37.0", "android-35", "android-34")) {
    foreach ($SdkRoot in $SdkRoots) {
        $CandidateJar = Join-Path $SdkRoot "platforms\$PlatformName\android.jar"
        if (Test-Path $CandidateJar) {
            $AndroidJar = $CandidateJar
            break
        }
    }
    if ($null -ne $AndroidJar) { break }
}

if ($null -eq $AndroidJar) {
    throw "No se encontro android.jar (android-36 / android-37.0 / android-35 / android-34) en: $($SdkRoots -join ', ')"
}

$Aapt2 = Join-Path $BuildToolsDir "aapt2.exe"
$Zipalign = Join-Path $BuildToolsDir "zipalign.exe"
$D8Jar = Join-Path $BuildToolsDir "lib\d8.jar"
$ApkSignerJar = Join-Path $BuildToolsDir "lib\apksigner.jar"

Write-Host "[SDK] build-tools: $BuildToolsDir"
Write-Host "[SDK] android.jar: $AndroidJar"

Write-Host "[1/7] Ejecutando pruebas unitarias (11 suites)..."
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "test-local.ps1")
if ($LASTEXITCODE -ne 0) { throw "Fallo en test-local.ps1" }

$BuildDir = Join-Path $ProjectRoot "build\apk-build"
$DistDir = Join-Path $ProjectRoot "dist"
if (Test-Path $BuildDir) {
    Remove-Item -Recurse -Force $BuildDir
}
New-Item -ItemType Directory -Force -Path "$BuildDir\compiled_res" | Out-Null
New-Item -ItemType Directory -Force -Path "$BuildDir\gen" | Out-Null
New-Item -ItemType Directory -Force -Path "$BuildDir\classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$BuildDir\dex" | Out-Null
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

$RawManifestPath = Join-Path $ProjectRoot "app\src\main\AndroidManifest.xml"
$ManifestContent = Get-Content -Path $RawManifestPath -Raw -Encoding UTF8
if ($ManifestContent -notmatch '<manifest[^>]*\bpackage=') {
    $ManifestContent = $ManifestContent -replace '<manifest\s+', '<manifest package="com.s23plus.camera" '
}
$StagedManifest = Join-Path $BuildDir "AndroidManifest.xml"
[System.IO.File]::WriteAllText($StagedManifest, $ManifestContent, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "[2/7] Compilando recursos Android con aapt2 (targetSdk 36, appId io.github.benedetto597.rescuecam)..."
& $Aapt2 compile --dir (Join-Path $ProjectRoot "app\src\main\res") -o "$BuildDir\compiled_res\res.zip"
if ($LASTEXITCODE -ne 0) { throw "Fallo en aapt2 compile" }

& $Aapt2 link `
    -I $AndroidJar `
    --manifest $StagedManifest `
    --rename-manifest-package io.github.benedetto597.rescuecam `
    --custom-package com.s23plus.camera `
    --min-sdk-version 30 `
    --target-sdk-version 36 `
    --version-code 7 `
    --version-name "2.1.0" `
    -o "$BuildDir\unaligned.apk" `
    --java "$BuildDir\gen" `
    "$BuildDir\compiled_res\res.zip"
if ($LASTEXITCODE -ne 0) { throw "Fallo en aapt2 link" }

Write-Host "[3/7] Compilando codigo fuente Java 17..."
$JavaSources = Get-ChildItem -Path (Join-Path $ProjectRoot "app\src\main\java"), "$BuildDir\gen" -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
& $Javac -encoding UTF-8 -source 17 -target 17 -classpath $AndroidJar -d "$BuildDir\classes" $JavaSources
if ($LASTEXITCODE -ne 0) { throw "Fallo en compilacion javac" }

Write-Host "[4/7] Convirtiendo bytecode a DEX con d8..."
$ClassFiles = Get-ChildItem -Path "$BuildDir\classes" -Recurse -Filter "*.class" | Select-Object -ExpandProperty FullName
& $Java -cp $D8Jar com.android.tools.r8.D8 --min-api 30 --lib $AndroidJar --output "$BuildDir\dex" $ClassFiles
if ($LASTEXITCODE -ne 0) { throw "Fallo en d8" }

Write-Host "[5/7] Empaquetando y alineando APK (zipalign)..."
Copy-Item "$BuildDir\unaligned.apk" "$BuildDir\unsigned.apk"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Zip = [System.IO.Compression.ZipFile]::Open("$BuildDir\unsigned.apk", [System.IO.Compression.ZipArchiveMode]::Update)
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($Zip, "$BuildDir\dex\classes.dex", "classes.dex") | Out-Null
$Zip.Dispose()

& $Zipalign -f -p 4 "$BuildDir\unsigned.apk" "$BuildDir\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "Fallo en zipalign" }

Write-Host "[6/7] Firmando APK (v1 + v2 + v3) con apksigner..."
$FinalApk = Join-Path $DistDir "camera-rescue-v2.1.0.apk"

$HasReleaseEnv = (-not [string]::IsNullOrWhiteSpace($env:ANDROID_KEYSTORE_PATH)) `
    -and (-not [string]::IsNullOrWhiteSpace($env:ANDROID_KEYSTORE_PASSWORD)) `
    -and (-not [string]::IsNullOrWhiteSpace($env:ANDROID_KEY_ALIAS)) `
    -and (-not [string]::IsNullOrWhiteSpace($env:ANDROID_KEY_PASSWORD))

if ($HasReleaseEnv) {
    if (-not (Test-Path $env:ANDROID_KEYSTORE_PATH)) {
        throw "ANDROID_KEYSTORE_PATH apunta a un archivo inexistente: $($env:ANDROID_KEYSTORE_PATH)"
    }
    Write-Host "[RELEASE-SIGN] Firmando APK con keystore de release ($($env:ANDROID_KEY_ALIAS))..."
    & $Java -jar $ApkSignerJar sign `
        --ks $env:ANDROID_KEYSTORE_PATH `
        --ks-pass "pass:$($env:ANDROID_KEYSTORE_PASSWORD)" `
        --ks-key-alias $env:ANDROID_KEY_ALIAS `
        --key-pass "pass:$($env:ANDROID_KEY_PASSWORD)" `
        --out $FinalApk `
        "$BuildDir\aligned.apk"
} else {
    Write-Host "[DEV-SIGN] Firmando APK con build\debug.keystore local (solo desarrollo)..."
    $Keystore = Join-Path $ProjectRoot "build\debug.keystore"
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Keystore) | Out-Null
    if (-not (Test-Path $Keystore)) {
        & $Keytool -genkeypair -v -keystore $Keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Fallo creando debug.keystore" }
    }
    & $Java -jar $ApkSignerJar sign `
        --ks $Keystore `
        --ks-pass pass:android `
        --ks-key-alias androiddebugkey `
        --key-pass pass:android `
        --out $FinalApk `
        "$BuildDir\aligned.apk"
}
if ($LASTEXITCODE -ne 0) { throw "Fallo en apksigner sign" }

Write-Host "[7/7] Verificando firma y metadatos del APK..."
& $Java -jar $ApkSignerJar verify --print-certs $FinalApk
if ($LASTEXITCODE -ne 0) { throw "Fallo en apksigner verify" }

$BadgingOutput = (& $Aapt2 dump badging $FinalApk 2>&1) | Out-String
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando aapt2 dump badging" }

$RequiredTokens = @(
    "name='io.github.benedetto597.rescuecam'",
    "versionCode='7'",
    "versionName='2.1.0'",
    "targetSdkVersion:'36'",
    "application-label:'Rescue Cam: Sensor Recovery'"
)
foreach ($Token in $RequiredTokens) {
    if (-not $BadgingOutput.Contains($Token)) {
        throw "Badging verification failed: missing '$Token'`n$BadgingOutput"
    }
}

$ForbiddenTokens = @(
    "android.permission.INTERNET",
    "BIND_ACCESSIBILITY_SERVICE"
)
foreach ($Token in $ForbiddenTokens) {
    if ($BadgingOutput.Contains($Token)) {
        throw "Badging verification failed: forbidden token found '$Token'`n$BadgingOutput"
    }
}

Write-Host "Badging verification PASSED (package, versionCode=7, versionName=2.1.0, targetSdk=36, label, 0 forbidden permissions)."
Write-Host "EXITO: APK generado, firmado y verificado en $FinalApk"
Get-Item $FinalApk | Select-Object FullName, Length, LastWriteTime
