$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ToolsRoot = "C:\Users\bened\.gemini\antigravity\brain\91cc6e57-4416-4b56-a368-6fa258b5dd9b\scratch\tools"

Write-Host "[1/5] Auditoria de seguridad Git (verificando que no haya keystores/secrets rastreados)..."
$TrackedFiles = & git -C $ProjectRoot ls-files
if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando git ls-files" }

$ForbiddenTracked = $TrackedFiles | Where-Object {
    $_ -match '(\.jks|\.keystore|\.p12|\.pk8|\.pem|\.aab)$' -or
    $_ -match '(^|/)keystore\.properties$' -or
    $_ -match '(^|/)signing\.properties$'
}
if ($ForbiddenTracked) {
    throw "SEGURIDAD: Archivos sensibles rastreados en Git:`n$($ForbiddenTracked -join "`n")"
}

Write-Host "[2/5] Verificando Gradle, jarsigner y configuracion de firma de release..."
$GradleWrapper = Join-Path $ProjectRoot "gradlew.bat"
$GradleCmd = $null
if (Test-Path $GradleWrapper) {
    $GradleCmd = $GradleWrapper
} else {
    $GlobalGradle = Get-Command gradle -ErrorAction SilentlyContinue
    if ($null -ne $GlobalGradle) {
        $GradleCmd = $GlobalGradle.Source
    }
}

if ($null -eq $GradleCmd) {
    throw "No se encontro '.\gradlew.bat' ni 'gradle' en PATH. Instala Gradle 8.11.1+ o agrega gradlew.bat, o usa el workflow manual .github/workflows/build-play-aab.yml en GitHub Actions."
}

$Jarsigner = $null
foreach ($Candidate in @(
    (Join-Path $ToolsRoot "jdk\jdk-17.0.21.0.2+1\bin\jarsigner.exe"),
    $(if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { Join-Path $env:JAVA_HOME "bin\jarsigner.exe" } else { "" })
)) {
    if (-not [string]::IsNullOrWhiteSpace($Candidate) -and (Test-Path $Candidate)) {
        $Jarsigner = $Candidate
        break
    }
}
if ($null -eq $Jarsigner) {
    $FromPath = Get-Command jarsigner -ErrorAction SilentlyContinue
    if ($null -ne $FromPath) {
        $Jarsigner = $FromPath.Source
    }
}
if ($null -eq $Jarsigner) {
    throw "No se encontro 'jarsigner' (JDK 17). Configura JAVA_HOME o agrega JDK bin al PATH."
}

$KeystorePropsPath = Join-Path $ProjectRoot "keystore.properties"
$HasKeystoreProps = Test-Path $KeystorePropsPath
$HasEnvSigning = (-not [string]::IsNullOrWhiteSpace($env:ANDROID_KEYSTORE_PATH)) `
    -and (-not [string]::IsNullOrWhiteSpace($env:ANDROID_KEYSTORE_PASSWORD)) `
    -and (-not [string]::IsNullOrWhiteSpace($env:ANDROID_KEY_ALIAS)) `
    -and (-not [string]::IsNullOrWhiteSpace($env:ANDROID_KEY_PASSWORD))

if (-not $HasKeystoreProps -and -not $HasEnvSigning) {
    throw @"
Falta configuracion de firma de release para generar un AAB firmado para Play Store.
Configura UNA de las siguientes opciones (nunca las subas a Git):
  1) Archivo 'keystore.properties' en la raiz del proyecto con:
     storeFile=ruta/al/upload-keystore.jks
     storePassword=...
     keyAlias=...
     keyPassword=...
  2) Variables de entorno:
     ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD
"@
}

if ($HasKeystoreProps) {
    $Props = @{}
    foreach ($Line in Get-Content $KeystorePropsPath) {
        $Trimmed = $Line.Trim()
        if ([string]::IsNullOrWhiteSpace($Trimmed) -or $Trimmed.StartsWith("#") -or $Trimmed.StartsWith("!")) {
            continue
        }
        $Idx = $Trimmed.IndexOf("=")
        if ($Idx -gt 0) {
            $Key = $Trimmed.Substring(0, $Idx).Trim()
            $Val = $Trimmed.Substring($Idx + 1).Trim()
            $Props[$Key] = $Val
        }
    }
    foreach ($RequiredKey in @("storeFile", "storePassword", "keyAlias", "keyPassword")) {
        if (-not $Props.ContainsKey($RequiredKey) -or [string]::IsNullOrWhiteSpace($Props[$RequiredKey])) {
            throw "keystore.properties invalido: falta la propiedad '$RequiredKey' o esta vacia."
        }
    }
    $ResolvedStoreFile = if ([System.IO.Path]::IsPathRooted($Props["storeFile"])) {
        $Props["storeFile"]
    } else {
        Join-Path $ProjectRoot $Props["storeFile"]
    }
    if (-not (Test-Path $ResolvedStoreFile)) {
        throw "El archivo storeFile indicado en keystore.properties no existe en disco: $ResolvedStoreFile"
    }
}

if ($HasEnvSigning -and -not (Test-Path $env:ANDROID_KEYSTORE_PATH)) {
    throw "ANDROID_KEYSTORE_PATH no existe en disco: $($env:ANDROID_KEYSTORE_PATH)"
}

Write-Host "[3/5] Ejecutando pruebas unitarias (test-local.ps1)..."
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "test-local.ps1")
if ($LASTEXITCODE -ne 0) { throw "Fallo en test-local.ps1" }

Write-Host "[4/5] Compilando Android App Bundle (bundleRelease)..."
Push-Location $ProjectRoot
try {
    & $GradleCmd bundleRelease --no-daemon --stacktrace
    if ($LASTEXITCODE -ne 0) { throw "Fallo ejecutando '$GradleCmd bundleRelease'" }
} finally {
    Pop-Location
}

$AabPath = Join-Path $ProjectRoot "app\build\outputs\bundle\release\app-release.aab"
if (-not (Test-Path $AabPath)) {
    throw "No se encontro el AAB esperado en: $AabPath"
}

Write-Host "[5/5] Verificando firma del AAB con jarsigner..."
$VerifyOutput = & $Jarsigner -verify -verbose -certs $AabPath 2>&1
$VerifyText = ($VerifyOutput | Out-String)
Write-Host $VerifyText
if ($LASTEXITCODE -ne 0 -or ($VerifyText -notmatch 'jar verified\.')) {
    throw "Fallo verificando firma del AAB con jarsigner (no se encontro 'jar verified.')"
}

Write-Host "EXITO: AAB de release firmado y verificado en $AabPath"
Get-Item $AabPath | Select-Object FullName, Length, LastWriteTime
