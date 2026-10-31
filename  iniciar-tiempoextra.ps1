
# ==========================================
# TIEMPO EXTRA - INICIO AUTOMATICO
# ==========================================

$ErrorActionPreference = "Stop"

# Ubicarse en TiempoExtraBackend2
Set-Location $PSScriptRoot

Write-Host "Iniciando Tiempo Extra..."

# Leer la contrasena de Oracle protegida
$rutaClave = Join-Path $env:APPDATA "TiempoExtra\oracle-password.dat"

if (!(Test-Path $rutaClave)) {
    throw "No se encontro la contrasena guardada de Oracle."
}

$claveSegura = (Get-Content $rutaClave -Raw).Trim() |
    ConvertTo-SecureString

$puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
    $claveSegura
)

try {
    $env:CANCHA_DB_PASSWORD =
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero)
}

# Buscar el archivo JAR del proyecto
$jar = Get-ChildItem ".\build\libs\*.jar" |
    Where-Object { $_.Name -notlike "*plain.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if ($null -eq $jar) {
    throw "No se encontro el JAR. Ejecuta gradlew.bat bootJar."
}

# Ejecutar Spring Boot
Write-Host "Arrancando servidor en http://localhost:8080"

java -jar $jar.FullName
