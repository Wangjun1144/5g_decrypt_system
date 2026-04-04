param(
    [string]$LogPath = (Join-Path $PSScriptRoot "build-link\\link.log"),
    [string]$OutputPath = (Join-Path $PSScriptRoot "build-link\\undefined-symbol-summary.txt")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $LogPath)) {
    throw "Link log not found: $LogPath"
}

$pattern = [regex]"undefined reference to ``([^']+)'" 
$symbols = @{}

Get-Content $LogPath | ForEach-Object {
    $match = $pattern.Match($_)
    if ($match.Success) {
        $symbol = $match.Groups[1].Value
        if (-not $symbols.ContainsKey($symbol)) {
            $symbols[$symbol] = 0
        }
        $symbols[$symbol]++
    }
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("Undefined symbol summary")
$lines.Add("Log: $LogPath")
$lines.Add("Generated: $(Get-Date -Format s)")
$lines.Add("")
$lines.Add("Unique symbols: $($symbols.Count)")
$lines.Add("")

$symbols.GetEnumerator() |
    Sort-Object -Property @{Expression = 'Value'; Descending = $true}, @{Expression = 'Name'; Descending = $false} |
    ForEach-Object {
        $lines.Add(("{0,5}  {1}" -f $_.Value, $_.Key))
    }

Set-Content -Path $OutputPath -Value $lines -Encoding UTF8
Write-Host "Wrote symbol summary: $OutputPath"
Write-Host "Unique symbols: $($symbols.Count)"
