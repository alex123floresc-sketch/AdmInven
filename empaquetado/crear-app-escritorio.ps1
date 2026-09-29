<#
.SYNOPSIS
    Crea la aplicación de escritorio de Windows (con su propio Java incluido) y la instala para el usuario actual.

.DESCRIPTION
    1. Compila el proyecto y ejecuta las pruebas (mvnw package).
    2. Con jpackage (incluido en el JDK) crea "Administrador de Inventario.exe", que no necesita tener Java instalado.
       También crea "Inventario Web.exe", que inicia el servidor para usar la aplicación desde el navegador.
    3. La copia en %LOCALAPPDATA%\Programs y crea accesos directos en el Escritorio y en el menú Inicio.
    Los datos se guardan en %USERPROFILE%\AdministradorInventario\data (no se borran al reinstalar).

.PARAMETER Tipo
    app-image (por omisión): carpeta con el .exe, sin instalador.
    exe o msi: instalador de Windows (requiere WiX Toolset: https://wixtoolset.org).

.PARAMETER SinInstalar
    Solo genera la aplicación en target\escritorio, sin copiarla ni crear accesos directos.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File empaquetado\crear-app-escritorio.ps1
#>
param(
    [ValidateSet('app-image', 'exe', 'msi')]
    [string]$Tipo = 'app-image',
    [switch]$SinInstalar
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
$nombre = 'Administrador de Inventario'
$version = '1.0.0'
Set-Location $raiz

Write-Host '1/3  Compilando y probando...' -ForegroundColor Cyan
& .\mvnw.cmd -B -q package
if ($LASTEXITCODE -ne 0) { throw 'La compilación o las pruebas fallaron.' }

# jpackage empaqueta todo lo que hay en la carpeta de entrada: solo se deja el JAR con dependencias.
$entrada = Join-Path $raiz 'target\jpackage-entrada'
$destino = Join-Path $raiz 'target\escritorio'
Remove-Item -Recurse -Force $entrada, $destino -ErrorAction SilentlyContinue
if (Test-Path $destino) {
    throw "No se pudo borrar $destino. Cierre la aplicación o las ventanas abiertas en esa carpeta."
}
New-Item -ItemType Directory -Force $entrada | Out-Null
Copy-Item 'target\administrador-de-inventario-1.0-SNAPSHOT.jar' (Join-Path $entrada 'inventario.jar')

Write-Host "2/3  Creando la aplicación ($Tipo) con jpackage..." -ForegroundColor Cyan
$argumentos = @(
    '--type', $Tipo,
    '--name', $nombre,
    '--app-version', $version,
    '--vendor', 'Administrador de Inventario',
    '--description', 'Gestión de inventario: productos, stock, ventas, compras y reportes',
    '--input', $entrada,
    '--main-jar', 'inventario.jar',
    '--main-class', 'inventario.Main',
    '--icon', 'empaquetado\icono.ico',
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--java-options', '-Dstdout.encoding=UTF-8',
    '--add-launcher', 'Inventario Web=empaquetado\lanzador-web.properties',
    '--dest', $destino
)
if ($Tipo -ne 'app-image') {
    # Instalador por usuario (no pide permisos de administrador) con accesos directos.
    $argumentos += '--win-per-user-install', '--win-shortcut', '--win-menu', '--win-menu-group', $nombre,
        '--win-dir-chooser', '--win-upgrade-uuid', '4d7c1f0e-6a55-4a0b-9d7e-2f6c9a1b8e31'
}
& jpackage @argumentos
if ($LASTEXITCODE -ne 0) { throw 'jpackage falló.' }

if ($Tipo -ne 'app-image') {
    Write-Host "Instalador listo en: $destino" -ForegroundColor Green
    exit 0
}
if ($SinInstalar) {
    Write-Host "Aplicación lista en: $destino\$nombre" -ForegroundColor Green
    exit 0
}

Write-Host '3/3  Instalando para el usuario actual...' -ForegroundColor Cyan
$programas = Join-Path $env:LOCALAPPDATA "Programs\$nombre"
if (Test-Path $programas) {
    # Si la aplicación está abierta, sus archivos no se pueden reemplazar.
    Get-Process | Where-Object { $_.Path -like "$programas\*" } | ForEach-Object {
        throw "Cierre '$($_.ProcessName)' antes de reinstalar."
    }
    Remove-Item -Recurse -Force $programas
}
Copy-Item -Recurse (Join-Path $destino $nombre) $programas

$shell = New-Object -ComObject WScript.Shell
$menuInicio = Join-Path ([Environment]::GetFolderPath('Programs')) $nombre
New-Item -ItemType Directory -Force $menuInicio | Out-Null
$accesos = @(
    @{ Carpeta = [Environment]::GetFolderPath('Desktop'); Nombre = $nombre; Exe = "$nombre.exe" },
    @{ Carpeta = $menuInicio; Nombre = $nombre; Exe = "$nombre.exe" },
    @{ Carpeta = $menuInicio; Nombre = 'Inventario Web (servidor)'; Exe = 'Inventario Web.exe' }
)
foreach ($a in $accesos) {
    $acceso = $shell.CreateShortcut((Join-Path $a.Carpeta "$($a.Nombre).lnk"))
    $acceso.TargetPath = Join-Path $programas $a.Exe
    $acceso.WorkingDirectory = $programas
    $acceso.IconLocation = (Join-Path $programas $a.Exe) + ',0'
    $acceso.Save()
}

Write-Host ''
Write-Host "Listo. Abra '$nombre' desde el Escritorio o el menú Inicio." -ForegroundColor Green
Write-Host "Programa: $programas"
Write-Host "Datos:    $env:USERPROFILE\AdministradorInventario\data"
