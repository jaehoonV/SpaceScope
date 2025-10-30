; SpaceScope_installer.iss (Ver 1.2.0)
#define MyAppVersion "1.2.0"

[Setup]
AppId={{D8F5F3D2-2A6A-4E7E-B7F9-3C728B59E42A}     ; 프로그램 고유 ID (변경 금지)
AppName=SpaceScope
AppVersion={#MyAppVersion}
AppPublisher=LEE JAEHOON
AppPublisherURL=https://github.com/jaehoonV/SpaceScope
AppSupportURL=https://github.com/jaehoonV/SpaceScope/issues
AppUpdatesURL=https://github.com/jaehoonV/SpaceScope/releases
DefaultDirName={autopf}\SpaceScope
DefaultGroupName=SpaceScope
OutputDir=..\v1.2.0
OutputBaseFilename=SpaceScope_Installer_v{#MyAppVersion}
SetupIconFile=..\SpaceScope\SpaceScope.ico
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
UninstallDisplayIcon={app}\SpaceScope.exe
WizardStyle=modern
Uninstallable=yes
UsePreviousAppDir=yes
LicenseFile=..\..\..\LICENSE
DisableWelcomePage=no
DisableDirPage=no
DisableProgramGroupPage=no
DisableReadyPage=no
DisableFinishedPage=no
SetupLogging=yes

[Files]
Source: "..\SpaceScope\SpaceScope.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\SpaceScope\SpaceScope.ico"; DestDir: "{app}"
Source: "..\SpaceScope\app\*"; DestDir: "{app}\app"; Flags: recursesubdirs createallsubdirs
Source: "..\SpaceScope\runtime\*"; DestDir: "{app}\runtime"; Flags: recursesubdirs createallsubdirs
Source: "..\..\..\LICENSE"; DestDir: "{app}"

[Tasks]
Name: "desktopicon"; Description: "바탕화면에 SpaceScope 바로가기 만들기"; GroupDescription: "추가 바로가기:"; Flags: checkedonce
Name: "startmenuicon"; Description: "시작 메뉴에 SpaceScope 바로가기 만들기"; GroupDescription: "추가 바로가기:"; Flags: checkedonce

[Icons]
; 시작 메뉴 바로가기
Name: "{group}\SpaceScope Ver 1.2.0"; Filename: "{app}\SpaceScope.exe"; IconFilename: "{app}\SpaceScope.ico"; Tasks: startmenuicon
; 바탕화면 바로가기
Name: "{commondesktop}\SpaceScope"; Filename: "{app}\SpaceScope.exe"; IconFilename: "{app}\SpaceScope.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\SpaceScope.exe"; Description: "SpaceScope 실행"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
