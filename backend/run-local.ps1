# Optional ignored local credentials file. Values are passed to Spring as environment variables.
$configPath = Join-Path $PSScriptRoot '.env.razorpay'
if (Test-Path -LiteralPath $configPath) {
    foreach ($entry in Get-Content -LiteralPath $configPath) {
        if ($entry -match '^(RAZORPAY_KEY_ID|RAZORPAY_KEY_SECRET)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
Set-Location -LiteralPath $PSScriptRoot
$env:AUTH_COOKIE_SECURE = 'false'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173'
mvn spring-boot:run
