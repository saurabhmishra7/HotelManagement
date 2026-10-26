param([string]$ConfigPath = "$PSScriptRoot\config.json")
$ErrorActionPreference='Stop'
. "$PSScriptRoot\printer.ps1"
$config=Get-Content -Raw $ConfigPath | ConvertFrom-Json
$mutex=New-Object Threading.Mutex($false,("Local\InnovaServePrinter-"+$config.device_id))
if(!$mutex.WaitOne(0)) { exit }
$journal=Join-Path $PSScriptRoot 'attempt.json'
$log=Join-Path $PSScriptRoot 'agent.log'
function Request-Api($path,$body=$null) {
  $args=@{Uri=($config.api_url.TrimEnd('/')+$path);Headers=@{'X-Print-Device-Key'=$config.device_key};TimeoutSec=20;ErrorAction='Stop'}
  if($null -ne $body) { $args.Method='POST';$args.ContentType='application/json';$args.Body=($body|ConvertTo-Json -Depth 20 -Compress) }
  return Invoke-RestMethod @args
}
function Save-Attempt($attempt) {
  $bytes=[Text.Encoding]::UTF8.GetBytes(($attempt|ConvertTo-Json -Compress))
  $file=[IO.File]::Open($journal+'.tmp',[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::None)
  try { $file.Write($bytes,0,$bytes.Length); $file.Flush($true) } finally { $file.Dispose() }
  Move-Item -Force ($journal+'.tmp') $journal
}
try {
  while($true) {
    try {
      if(Test-Path $journal) {
        $attempt=Get-Content -Raw $journal|ConvertFrom-Json
        $state=if($attempt.status){$attempt.status}else{'uncertain'}
        try {
          $null=Request-Api ("/printer-devices/"+$config.device_id+"/jobs/"+$attempt.id+"/status") @{claim_id=$attempt.claim_id;status=$state;detail=$attempt.detail}
        } catch {
          if(!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 409) { throw }
        }
        Remove-Item $journal
      }
      $result=Request-Api ("/printer-devices/pending-jobs?device_id="+$config.device_id)
      if($result.jobs.Count -gt 0) {
        $job=$result.jobs[0]
        $attempt=@{id=$job.id;claim_id=$job.claim_id;status='uncertain';detail='Agent stopped during output; check the paper'}
        Save-Attempt $attempt
        try {
          Send-Ticket $config $job.snapshot
          $attempt.status='printed';$attempt.detail='Windows spooler or TCP accepted bytes; physical output not independently verified'
        } catch { $attempt.status='uncertain';$attempt.detail='Printer write failed; physically check before retrying' }
        Save-Attempt $attempt
      }
    } catch {
      if((Test-Path $log) -and (Get-Item $log).Length -gt 1MB) { Remove-Item $log }
      Add-Content $log ((Get-Date -Format o)+' Printer or server unavailable; open Station printers to review.')
    }
    Start-Sleep -Seconds 5
  }
} finally { $mutex.ReleaseMutex();$mutex.Dispose() }
