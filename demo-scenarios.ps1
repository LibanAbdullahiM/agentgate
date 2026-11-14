# AgentGate demo scenarios. Works in Windows PowerShell 5.1 and PowerShell 7.   Run:  .\demo-scenarios.ps1
# Needs VALIO_MODE=mock (products 460000 / 460002 / 460063 behave in a fixed way there).
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api/v1"
$neededBy = (Get-Date).AddDays(3).ToString("yyyy-MM-dd")

function Login($user) {
    $r = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" `
        -Body (@{ username = $user; password = "demo1234" } | ConvertTo-Json)
    @{ Authorization = "Bearer $($r.accessToken)" }
}
function Json($o) { $o | ConvertTo-Json -Depth 6 }
function Quote($code, $qty) {
    Invoke-RestMethod -Method Post -Uri "$base/quotes" -Headers $agent -ContentType "application/json" `
        -Body (Json @{ productCode = $code; quantity = $qty; neededBy = $neededBy })
}
function Order($quoteId) {
    Invoke-RestMethod -Method Post -Uri "$base/orders" `
        -Headers ($agent + @{ "Idempotency-Key" = [guid]::NewGuid().ToString() }) `
        -ContentType "application/json" -Body (Json @{ quoteId = $quoteId })
}
# One line per audit event. foreach (not a pipeline) so it also works when PowerShell 5.1 returns a JSON array as one object.
function Trace($orderId) {
    $events = Invoke-RestMethod -Uri "$base/orders/$orderId/trace" -Headers $agent
    foreach ($e in $events) { "  - " + $e.summary }
}
# The error body (code + message) of a failed HTTP call, in both PowerShell versions.
function ErrBody($err) {
    if ($err.ErrorDetails -and $err.ErrorDetails.Message) { return $err.ErrorDetails.Message }
    try {
        $reader = New-Object System.IO.StreamReader($err.Exception.Response.GetResponseStream())
        return $reader.ReadToEnd()
    } catch { return $err.Exception.Message }
}
function Title($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }

$admin = Login "buyer-admin"; $approver = Login "approver"; $agent = Login "agent-1"
$mandates = Invoke-RestMethod -Uri "$base/mandates" -Headers $admin
$mandate = @($mandates)[0]

Title "1. Happy path: small order inside the mandate -> confirmed by the agent alone"
$q = Quote "460000" 2
$o = Order $q.id
"status: $($o.status)  total: $($o.total) $($o.currency)  codes: $($o.decisionCodes -join ',')"
Trace $o.id

Title "2. Over the limit -> escalated to a human, who approves"
$q = Quote "460000" 60
$o = Order $q.id
"status: $($o.status)  total: $($o.total)  codes: $($o.decisionCodes -join ',')"
$inbox = Invoke-RestMethod -Uri "$base/approvals" -Headers $approver
"inbox now holds: " + @($inbox).Count + " order(s)"
$d = Invoke-RestMethod -Method Post -Uri "$base/approvals/$($o.id)/approve" -Headers $approver `
    -ContentType "application/json" -Body (Json @{ comment = "Banquet on Saturday, OK." })
"after approval: $($d.status) by $($d.decidedBy)"
Trace $o.id

Title "3. Allergen block: buyer excludes egg (AE); the product contains it -> refused"
Invoke-RestMethod -Method Patch -Uri "$base/mandates/$($mandate.id)" -Headers $admin -ContentType "application/json" `
    -Body (Json @{ avoidAllergens = @("AE") }) | Out-Null
$q = Quote "460000" 2
$o = Order $q.id
"status: $($o.status)  codes: $($o.decisionCodes -join ',')"
"reason: $($o.decisionReason)"
Invoke-RestMethod -Method Patch -Uri "$base/mandates/$($mandate.id)" -Headers $admin -ContentType "application/json" `
    -Body (Json @{ avoidAllergens = @() }) | Out-Null

Title "4. Unconfirmed stock (terminal product) -> escalated, then rejected by the approver"
$q = Quote "460002" 2
"quote stockConfirmed: $($q.stockConfirmed)"
$o = Order $q.id
"status: $($o.status)  codes: $($o.decisionCodes -join ',')"
$d = Invoke-RestMethod -Method Post -Uri "$base/approvals/$($o.id)/reject" -Headers $approver `
    -ContentType "application/json" -Body (Json @{ comment = "Cannot risk an unconfirmed delivery for this event." })
"after rejection: $($d.status)"
Trace $o.id

Title "5. Product blocked by Valio -> no quote can be issued"
try { Quote "460063" 2 | Out-Null; "UNEXPECTED: a quote was issued" } catch { "refused: " + (ErrBody $_) }

Title "6. Wrong lot size -> refused before any price is asked"
try { Quote "460000" 0.005 | Out-Null; "UNEXPECTED: a quote was issued" } catch { "refused: " + (ErrBody $_) }

Title "Live feed (what the frontend's activity screen polls)"
$feed = Invoke-RestMethod -Uri "$base/audit?limit=8" -Headers $admin
foreach ($e in $feed) { "  #$($e.id) " + $e.summary }
