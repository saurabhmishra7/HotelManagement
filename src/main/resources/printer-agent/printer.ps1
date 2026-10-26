$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class InnovaRawPrinter {
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
  public class Doc { [MarshalAs(UnmanagedType.LPWStr)] public string Name="InnovaServe KOT";
    [MarshalAs(UnmanagedType.LPWStr)] public string Output=null;
    [MarshalAs(UnmanagedType.LPWStr)] public string Type="RAW"; }
  [DllImport("winspool.drv", EntryPoint="OpenPrinterW", CharSet=CharSet.Unicode, SetLastError=true)]
  static extern bool OpenPrinter(string name, out IntPtr handle, IntPtr defaults);
  [DllImport("winspool.drv", EntryPoint="StartDocPrinterW", CharSet=CharSet.Unicode, SetLastError=true)]
  static extern int StartDocPrinter(IntPtr handle, int level, [In] Doc doc);
  [DllImport("winspool.drv", SetLastError=true)] static extern bool WritePrinter(IntPtr handle, IntPtr data, int count, out int written);
  [DllImport("winspool.drv")] static extern bool EndDocPrinter(IntPtr handle);
  [DllImport("winspool.drv")] static extern bool ClosePrinter(IntPtr handle);
  public static void Send(string name, byte[] bytes) {
    IntPtr handle, data=IntPtr.Zero; bool started=false;
    if(!OpenPrinter(name,out handle,IntPtr.Zero)) throw new Exception("Cannot open Windows printer");
    try {
      if(StartDocPrinter(handle,1,new Doc())==0) throw new Exception("Cannot start RAW print job");
      started=true; data=Marshal.AllocHGlobal(bytes.Length); Marshal.Copy(bytes,0,data,bytes.Length);
      int written;
      if(!WritePrinter(handle,data,bytes.Length,out written) || written!=bytes.Length) throw new Exception("Incomplete printer write");
      if(!EndDocPrinter(handle)) throw new Exception("Spooler did not confirm job completion");
      started=false;
    } finally { if(data!=IntPtr.Zero) Marshal.FreeHGlobal(data); if(started) EndDocPrinter(handle); ClosePrinter(handle); }
  }
}
'@
function Clean-PrintText($value) {
  return ([string]$value -replace '[\x00-\x1f\x7f]', ' ' -replace '[^\x20-\x7e]', '?')
}
function Send-Ticket($config, $ticket) {
  $lines = @((Clean-PrintText $ticket.property), (Clean-PrintText $ticket.title))
  if($ticket.gstin) { $lines += 'GSTIN: ' + (Clean-PrintText $ticket.gstin) }
  if($ticket.address) { $lines += Clean-PrintText $ticket.address }
  if($ticket.table_number) { $lines += 'Table: ' + (Clean-PrintText $ticket.table_number) }
  if($ticket.room_number) { $lines += 'Room: ' + (Clean-PrintText $ticket.room_number) }
  if($ticket.order_id) { $lines += 'Order: ' + (Clean-PrintText $ticket.order_id) }
  if($ticket.created_at) { $lines += ([DateTimeOffset]::Parse($ticket.created_at)).LocalDateTime.ToString() }
  $width = if($config.paper_width -eq 58) { 32 } else { 42 }
  $lines += '-' * $width
  foreach($item in $ticket.items) {
    $name = if($item.name) { $item.name } else { $item.description }
    $lines += (Clean-PrintText $item.quantity) + ' x ' + (Clean-PrintText $name)
    if($item.notes) { $lines += 'Note: ' + (Clean-PrintText $item.notes) }
    if($null -ne $item.line_total) { $lines += 'Line: INR ' + $item.line_total + ' Tax: INR ' + $item.tax_amount }
  }
  if($null -ne $ticket.total_amount) { $lines += 'Subtotal: INR ' + $ticket.subtotal; $lines += 'Tax: INR ' + $ticket.tax_amount; $lines += 'Total: INR ' + $ticket.total_amount }
  if($null -ne $ticket.amount_due) { $lines += 'Paid: INR ' + $ticket.paid_total; $lines += 'Credits: INR ' + $ticket.credit_total; $lines += 'Due: INR ' + $ticket.amount_due }
  $wrapped = foreach($line in $lines) { if(!$line) { '' } else { for($i=0; $i -lt $line.Length; $i+=$width) { $line.Substring($i,[Math]::Min($width,$line.Length-$i)) } } }
  [byte[]]$bytes = @(27,64) + [Text.Encoding]::ASCII.GetBytes(($wrapped -join [char]10) + ([string][char]10)*3) + @(29,86,66,0)
  if($config.transport -eq 'tcp') {
    $client = New-Object Net.Sockets.TcpClient
    try {
      $connect = $client.ConnectAsync($config.printer_host,[int]$config.printer_port)
      if(!$connect.Wait(10000)) { throw 'Printer connection timed out' }
      $stream=$client.GetStream(); $stream.WriteTimeout=15000
      $stream.Write($bytes,0,$bytes.Length); $stream.Flush()
    } finally { $client.Dispose() }
  } else { [InnovaRawPrinter]::Send($config.printer_name,$bytes) }
}
