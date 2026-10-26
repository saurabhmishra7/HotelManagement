InnovaServe Windows Print Agent

1. On the Windows 10/11 printer PC, extract this ZIP into its own folder.
2. Open Setup.cmd. Choose an installed Windows printer (USB/driver) or an ESC/POS
   network TCP printer. Enter a reachable backend API URL if necessary.
3. Confirm the test slip, then return to Station printers, Refresh and assign a purpose.

No npm/Node.js installation is needed. Setup uses Windows PowerShell 5.1,
the Windows spooler or TCP, and Task Scheduler under the signed-in user.
The PC must stay awake, online and signed in. Managed PCs may need IT to permit scripts.
Unsupported printer languages are not converted; the agent sends RAW ESC/POS.

The ZIP contains a private printer key. Transfer it privately and delete the ZIP and
extracted pairing.json after setup. Local credentials/journal live in
%LOCALAPPDATA%\InnovaServe\Printers\<device id> with restricted folder permissions.
Downloading another setup revokes the previous key. Deactivate a lost device in the portal.

An Uncertain ticket is never silently printed again. Check paper in the kitchen,
then choose Check & retry or Already printed in Station printers.
Printed means Windows/TCP accepted output, not proof of physical paper.
Initial ESC/POS output supports ASCII; other characters print as ?.

To uninstall, deactivate the printer in the portal, stop/delete its
InnovaServe Printer task in Task Scheduler and delete its local folder.
This agent is Windows-only. macOS/Linux can use compatible browser USB/ePOS HTTPS
printers; other setups need a platform-specific bridge.
