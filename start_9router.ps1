# Start 9Router Gateway with Node v24 on port 20128
# Bind to 127.0.0.1 to avoid port collisions with Tailscale serve

$ErrorActionPreference = "Stop"

$NodePath = "C:\Users\kieIC\AppData\Local\OpenAI\Codex\runtimes\cua_node\cfb32733c877621e\bin\node.exe"
if (-not (Test-Path $NodePath)) {
    $NodePath = "C:\Users\kieIC\AppData\Roaming\npm\node.exe"
}

$CliPath = "C:\Users\kieIC\AppData\Roaming\npm\node_modules\9router\cli.js"

Write-Host "🚀 Starting 9Router Gateway..." -ForegroundColor Cyan
Write-Host "   Node Binary: $NodePath" -ForegroundColor Gray
Write-Host "   CLI Script:  $CliPath" -ForegroundColor Gray
Write-Host "   Port:        20128 (127.0.0.1)" -ForegroundColor Gray

& $NodePath $CliPath -n --skip-update -H 127.0.0.1 -p 20128 -l
