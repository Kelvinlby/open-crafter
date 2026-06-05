; Inno Setup script for Open Crafter (Windows installer).
; Build the app first (flutter build windows --release), then compile with:
;   ISCC.exe /DAppVersion=1.0.0 packaging\windows\open_crafter.iss
; Produces packaging\windows\installer\open_crafter-<version>-setup.exe

#define AppName "Open Crafter"
#define AppPublisher "Kelvin LBY"
#define AppExeName "open_crafter.exe"

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif

[Setup]
; AppId uniquely identifies this application; keep it stable across releases so
; installers upgrade in place instead of installing side-by-side.
AppId={{284A2578-3C8F-4FE5-B5AF-46074BECFF18}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\Open Crafter
DefaultGroupName=Open Crafter
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#AppExeName}
OutputBaseFilename=open_crafter-{#AppVersion}-setup
OutputDir=installer
SetupIconFile=..\..\windows\runner\resources\app_icon.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\..\build\windows\x64\runner\Release\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\Open Crafter"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\Open Crafter"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "{cm:LaunchProgram,Open Crafter}"; Flags: nowait postinstall skipifsilent
