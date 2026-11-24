param(
    [string]$Sdk = 'D:\Android\Sdk',
    [string]$VersionName = '1.1.0',
    [int]$VersionCode = 4
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$realRoot = $PSScriptRoot
$root = $realRoot
if ($realRoot -match '[^\x00-\x7F]') {
    # aapt2 on Windows cannot open non-ASCII paths; expose the project through an ASCII junction.
    $link = 'C:\OhWhaleBuildSrc'
    $needLink = $true
    if (Test-Path $link) {
        $item = Get-Item $link -Force
        if ($item.LinkType -eq 'Junction' -and "$($item.Target)" -eq $realRoot) {
            $needLink = $false
        } else {
            Remove-Item $link -Force -Recurse
        }
    }
    if ($needLink) {
        New-Item -ItemType Junction -Path $link -Target $realRoot | Out-Null
    }
    $root = $link
}
$bt = Join-Path $Sdk 'build-tools\35.0.1'
$platform = Join-Path $Sdk 'platforms\android-35\android.jar'
$build = Join-Path $root 'build'
$dist = Join-Path $root 'dist'
$res = Join-Path $root 'app\src\main\res'
$assets = Join-Path $root 'app\src\main\assets'
$javaSrc = Join-Path $root 'app\src\main\java'
$manifest = Join-Path $root 'app\src\main\AndroidManifest.xml'
$ks = Join-Path $root 'keystore\ohwhale-release.jks'
$ksProps = Join-Path $root 'keystore\keystore.properties'
$apkName = "OhWhale-$VersionName.apk"

foreach ($p in @($bt, $platform, $res, $assets, $manifest, $ksProps)) {
    if (-not (Test-Path $p)) { throw "Missing build dependency: $p" }
}
foreach ($tool in @('aapt2.exe', 'zipalign.exe', 'apksigner.bat', 'd8.bat')) {
    if (-not (Test-Path (Join-Path $bt $tool))) { throw "Missing build-tools executable: $tool" }
}

if (-not (Test-Path $ks)) {
    Write-Host 'Generating signing key (first build)...' -ForegroundColor Cyan
    $props = @{}
    Get-Content $ksProps | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') { $props[$matches[1].Trim()] = $matches[2].Trim() }
    }
    $keytoolArgs = @(
        '-genkeypair', '-v', '-keystore', $ks, '-storetype', 'PKCS12',
        '-storepass', $props['storePassword'], '-keypass', $props['keyPassword'],
        '-alias', $props['keyAlias'], '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '10000',
        '-dname', 'CN=OhWhale, OU=Mobile, O=OhWhale, L=Shanghai, ST=Shanghai, C=CN'
    )
    & keytool @keytoolArgs
    if ($LASTEXITCODE -ne 0) { throw 'Failed to generate signing key' }
}

Write-Host 'Cleaning build directory...' -ForegroundColor Cyan
Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $build, (Join-Path $build 'gen'), (Join-Path $build 'classes'), (Join-Path $build 'dex'), $dist | Out-Null

Write-Host 'Compiling resources...' -ForegroundColor Cyan
& (Join-Path $bt 'aapt2.exe') compile --dir $res -o (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host 'Linking resources and manifest...' -ForegroundColor Cyan
& (Join-Path $bt 'aapt2.exe') link `
    -o (Join-Path $build 'base.apk') `
    -I $platform `
    --manifest $manifest `
    -R (Join-Path $build 'res.zip') `
    --java (Join-Path $build 'gen') `
    --min-sdk-version 24 `
    --target-sdk-version 34 `
    --version-code $VersionCode `
    --version-name $VersionName `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host 'Compiling Java...' -ForegroundColor Cyan
$javaFiles = @(
    Get-ChildItem $javaSrc -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    Get-ChildItem (Join-Path $build 'gen') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
)
& javac -encoding UTF-8 -source 11 -target 11 -Xlint:-options `
    -classpath $platform -d (Join-Path $build 'classes') $javaFiles
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host 'Generating dex with d8...' -ForegroundColor Cyan
$classFiles = @(Get-ChildItem (Join-Path $build 'classes') -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& (Join-Path $bt 'd8.bat') --lib $platform --min-api 24 --output (Join-Path $build 'dex') $classFiles
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host 'Packaging APK (classes.dex + assets)...' -ForegroundColor Cyan
$unsigned = Join-Path $build 'unsigned.apk'
$baseApk = Join-Path $build 'base.apk'
$dexFile = Join-Path $build 'dex\classes.dex'
$stage = Join-Path $build 'stage'

# Android 11+ requires resources.arsc to be stored (not deflated) and 4-byte
# aligned. Repack from an extracted staging directory with tar's zip writer,
# forcing store compression; zipalign then fixes alignment before signing.
Add-Type -AssemblyName System.IO.Compression.FileSystem
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
[System.IO.Compression.ZipFile]::ExtractToDirectory($baseApk, $stage)
Copy-Item $dexFile (Join-Path $stage 'classes.dex') -Force
Copy-Item $assets (Join-Path $stage 'assets') -Recurse -Force

$tar = Get-Command tar.exe -ErrorAction SilentlyContinue
if (-not $tar) { throw 'tar.exe not found; Windows 10 1803+ is required' }
& $tar.Source --format zip --options zip:compression=store -cf $unsigned `
    -C $stage AndroidManifest.xml res resources.arsc classes.dex assets
if ($LASTEXITCODE -ne 0) { throw 'tar packaging failed' }

Write-Host 'Aligning APK...' -ForegroundColor Cyan
$aligned = Join-Path $build 'aligned.apk'
& (Join-Path $bt 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$props = @{}
Get-Content $ksProps | ForEach-Object {
    if ($_ -match '^\s*([^#=]+)=(.*)$') { $props[$matches[1].Trim()] = $matches[2].Trim() }
}
$outApk = Join-Path $dist $apkName
Write-Host 'Signing APK...' -ForegroundColor Cyan
& (Join-Path $bt 'apksigner.bat') sign `
    --ks $ks `
    --ks-key-alias $props['keyAlias'] `
    --ks-pass ("pass:" + $props['storePassword']) `
    --key-pass ("pass:" + $props['keyPassword']) `
    --min-sdk-version 24 `
    --v1-signing-enabled true `
    --v2-signing-enabled true `
    --v3-signing-enabled true `
    --v4-signing-enabled false `
    --out $outApk `
    $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host 'Verifying signature...' -ForegroundColor Cyan
& (Join-Path $bt 'apksigner.bat') verify --verbose --print-certs $outApk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }

Copy-Item $outApk (Join-Path $dist 'OhWhale-latest.apk') -Force
$sizeMb = [Math]::Round((Get-Item $outApk).Length / 1MB, 2)
$sha = (Get-FileHash $outApk -Algorithm SHA256).Hash
Write-Host ''
Write-Host "Build complete: $outApk" -ForegroundColor Green
Write-Host "Size: $sizeMb MB"
Write-Host "SHA256: $sha"
Write-Host "Version: $VersionName ($VersionCode)"
