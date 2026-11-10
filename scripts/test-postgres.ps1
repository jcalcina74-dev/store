param(
 [string]$PgBin = 'C:\Program Files\PostgreSQL\18\bin',
 [int]$Port = 55439
)
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$cluster = Join-Path $projectRoot ('target\pg-test-' + [Guid]::NewGuid().ToString('N'))
$previousUrl = $env:TEST_DB_URL
$previousUser = $env:TEST_DB_USERNAME
$previousPassword = $env:TEST_DB_PASSWORD
try {
 [System.IO.Directory]::CreateDirectory((Split-Path $cluster)) | Out-Null
 & (Join-Path $PgBin 'initdb.exe') -D $cluster -U storetest -A trust --encoding=UTF8 --locale=C
 if ($LASTEXITCODE -ne 0) { throw 'Fallo initdb' }
 $pgArgs = @('-D', ('"' + $cluster + '"'), '-l', ('"' + $cluster + '.log"'), '-o', ('"-h 127.0.0.1 -p ' + $Port + '"'), '-w', 'start')
 $starter = Start-Process -FilePath (Join-Path $PgBin 'pg_ctl.exe') -ArgumentList $pgArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput ($cluster + '.start.out') -RedirectStandardError ($cluster + '.start.err')
 if (-not $starter.WaitForExit(30000)) { throw 'Timeout al iniciar PostgreSQL temporal' }
 if (-not (Test-Path -LiteralPath (Join-Path $cluster 'postmaster.pid'))) { throw 'No se pudo iniciar PostgreSQL temporal' }
 & (Join-Path $PgBin 'pg_isready.exe') -h 127.0.0.1 -p $Port -U storetest -d postgres
 if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL temporal no esta listo' }
 $env:TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Port/postgres"
 $env:TEST_DB_USERNAME = 'storetest'
 $env:TEST_DB_PASSWORD = ''
 Push-Location $projectRoot
 try {
  & .\mvnw.cmd -B -ntp verify
  if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas con PostgreSQL' }
 } finally { Pop-Location }
} finally {
 if (Test-Path -LiteralPath (Join-Path $cluster 'postmaster.pid')) {
  & (Join-Path $PgBin 'pg_ctl.exe') -D $cluster -m fast -w stop
 }
 $env:TEST_DB_URL = $previousUrl
 $env:TEST_DB_USERNAME = $previousUser
 $env:TEST_DB_PASSWORD = $previousPassword
}