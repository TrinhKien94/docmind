# Start 9Router Gateway with Node on port 20128
# Bind to 127.0.0.1 to avoid port collisions with Tailscale serve

$ErrorActionPreference = "Stop"

$NodeCandidates = @(
    "C:\Users\kieIC\AppData\Local\OpenAI\Codex\runtimes\cua_node\cfb32733c877621e\bin\node.exe",
    "C:\Users\kieIC\AppData\Roaming\npm\node.exe",
    "C:\Program Files\nodejs\node.exe"
)

$NodePath = $null
foreach ($cand in $NodeCandidates) {
    if (Test-Path $cand) {
        $NodePath = $cand
        break
    }
}

if (-not $NodePath) {
    $cmd = Get-Command node -ErrorAction SilentlyContinue
    if ($cmd) { $NodePath = $cmd.Source }
}

if (-not $NodePath) {
    throw "Node.exe not found on system."
}

$CliPath = "C:\Users\kieIC\AppData\Roaming\npm\node_modules\9router\cli.js"
if (-not (Test-Path $CliPath)) {
    throw "9Router CLI script not found at $CliPath"
}

Write-Host "🚀 Starting 9Router Gateway..." -ForegroundColor Cyan
Write-Host "   Node Binary: $NodePath" -ForegroundColor Gray
Write-Host "   CLI Script:  $CliPath" -ForegroundColor Gray
Write-Host "   Port:        20128 (127.0.0.1)" -ForegroundColor Gray

& $NodePath $CliPath -n --skip-update -H 127.0.0.1 -p 20128 -l
