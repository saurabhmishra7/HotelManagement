$ErrorActionPreference='Stop'
. "$PSScriptRoot\printer.ps1"
$pair=Get-Content -Raw "$PSScriptRoot\pairing.json" | ConvertFrom-Json
$taskName='InnovaServe Printer '+$pair.device_id
$dest=Join-Path $env:LOCALAPPDATA ('InnovaServe\Printers\'+$pair.device_id)
New-Item -ItemType Directory -Force $dest | Out-Null
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $dest '/inheritance:r' '/grant:r' "$($identity):(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if($LASTEXITCODE -ne 0) { throw 'Could not protect the local printer credentials' }
Write-Host 'InnovaServe printer setup'
Write-Host 'Use an ESC/POS compatible thermal printer. No npm or Node.js installation is needed.'
$api=Read-Host ("Backend API URL [Enter for "+$pair.api_url+"]")
if(!$api) { $api=$pair.api_url }
$uri=[Uri]$api
if(!$uri.IsAbsoluteUri -or $uri.Scheme -notin @('https','http')) { throw 'Enter an HTTP(S) API URL' }
if($uri.Scheme -eq 'http') { Write-Host 'HTTP is for trusted local development only. Use HTTPS for production.' }
$config=@{device_id=$pair.device_id;device_key=$pair.device_key;api_url=$api.TrimEnd('/');paper_width=80}
$choice=Read-Host 'Connection: 1 = installed Windows printer / USB, 2 = network TCP [1]'
if($choice -eq '2') {
  $config.transport='tcp'; $config.printer_host=Read-Host 'Printer IP or hostname'
  $port=Read-Host 'Printer TCP port [9100]'; $config.printer_port=if($port){[int]$port}else{9100}
  if(!$config.printer_host -or $config.printer_port -lt 1 -or $config.printer_port -gt 65535) { throw 'Invalid printer host/port' }
} else {
  $config.transport='windows'
  $printers=@(Get-Printer | Sort-Object Name)
  if(!$printers.Count) { throw 'Install the printer in Windows Settings first, then run Setup.cmd again' }
  for($i=0;$i -lt $printers.Count;$i++) { Write-Host ("[" + ($i+1) + "] " + $printers[$i].Name) }
  $selected=[int](Read-Host 'Choose printer number')
  if($selected -lt 1 -or $selected -gt $printers.Count) { throw 'Invalid printer selection' }
  $config.printer_name=$printers[$selected-1].Name
}
$paper=Read-Host 'Paper width: 58 or 80 mm [80]'
if($paper) { $config.paper_width=[int]$paper }
if($config.paper_width -notin @(58,80)) { throw 'Choose 58 or 80 mm' }
$old=Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if($old) { Stop-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue }
Send-Ticket $config @{property='InnovaServe';title='Printer test';items=@(@{quantity=1;name='Test item';notes='Check all text is readable'})}
if((Read-Host 'Did a complete, readable test slip print? Type YES') -ne 'YES') { throw 'Setup stopped. Check the printer before using live orders.' }
$null=Invoke-RestMethod -Uri ($config.api_url+'/printer-devices/'+$config.device_id+'/agent-tested') -Method Post -Headers @{'X-Print-Device-Key'=$config.device_key} -TimeoutSec 20
foreach($name in @('agent.ps1','printer.ps1')) { Copy-Item -Force "$PSScriptRoot\$name" $dest }
$config|ConvertTo-Json|Set-Content -Encoding UTF8 (Join-Path $dest 'config.json')
$action=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument ('-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+(Join-Path $dest 'agent.ps1')+'"')
$trigger=New-ScheduledTaskTrigger -AtLogOn -User $identity
$settings=New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$principal=New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Start-ScheduledTask -TaskName $taskName
Write-Host 'Setup complete. In Station printers, refresh and assign this printer to a purpose.'
Write-Host 'It starts automatically when this Windows user signs in. Keep this PC awake and connected.'
Write-Host 'The ZIP contains a private key. Delete the ZIP and extracted pairing.json after installation.'
