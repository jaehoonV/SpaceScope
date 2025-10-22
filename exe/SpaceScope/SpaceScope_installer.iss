; SpaceScope_installer.iss
#define MyAppVersion "1.0.0"

[Setup]
AppId={{D8F5F3D2-2A6A-4E7E-B7F9-3C728B59E42A}     ; 프로그램 고유 ID (변경 금지)
AppName=SpaceScope
AppVersion={#MyAppVersion}
AppPublisher=LEE JAEHOON
DefaultDirName={autopf}\SpaceScope
DefaultGroupName=SpaceScope
OutputDir=..\SpaceScope
OutputBaseFilename=SpaceScope_Installer_v{#MyAppVersion}
SetupIconFile=SpaceScope.ico
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
UninstallDisplayIcon={app}\SpaceScope.exe
WizardStyle=modern
Uninstallable=yes
UsePreviousAppDir=yes

[Files]
Source: "SpaceScope.exe"; DestDir: "{app}"
Source: "SpaceScope.ico"; DestDir: "{app}"
Source: "app\*"; DestDir: "{app}\app"; Flags: recursesubdirs createallsubdirs
Source: "runtime\*"; DestDir: "{app}\runtime"; Flags: recursesubdirs createallsubdirs

[Tasks]
Name: "desktopicon"; Description: "바탕화면에 SpaceScope 바로가기 만들기"; GroupDescription: "추가 바로가기:"; Flags: checkedonce
Name: "startmenuicon"; Description: "시작 메뉴에 SpaceScope 바로가기 만들기"; GroupDescription: "추가 바로가기:"; Flags: checkedonce

[Icons]
; 시작 메뉴 바로가기 (Tasks로 제어됨)
Name: "{group}\SpaceScope"; Filename: "{app}\SpaceScope.exe"; IconFilename: "{app}\SpaceScope.ico"; Tasks: startmenuicon
; 바탕화면 바로가기 (Tasks로 제어됨)
Name: "{commondesktop}\SpaceScope"; Filename: "{app}\SpaceScope.exe"; IconFilename: "{app}\SpaceScope.ico"; Tasks: desktopicon



[Run]
Filename: "{app}\SpaceScope.exe"; Description: "SpaceScope 실행"; Flags: nowait postinstall skipifsilent
