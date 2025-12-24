; SpaceScope_installer.iss (Ver 1.2.1)
#define MyAppVersion "1.2.1"

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
OutputDir=..\v{#MyAppVersion}
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

[CustomMessages]
; --- Task 그룹/설명
korean.AddShortcutsGroup=추가 바로가기:
english.AddShortcutsGroup=Additional shortcuts:

korean.TaskDesktop=바탕화면에 SpaceScope 바로가기 만들기
english.TaskDesktop=Create a SpaceScope desktop shortcut

korean.TaskStartMenu=시작 메뉴에 SpaceScope 바로가기 만들기
english.TaskStartMenu=Create a SpaceScope Start Menu shortcut

; --- Icons(바로가기 이름)
korean.StartMenuShortcutName=SpaceScope Ver {#MyAppVersion}
english.StartMenuShortcutName=SpaceScope v{#MyAppVersion}

korean.DesktopShortcutName=SpaceScope
english.DesktopShortcutName=SpaceScope

; --- Run 문구
korean.RunApp=SpaceScope 실행
english.RunApp=Launch SpaceScope

[Files]
Source: "..\SpaceScope\SpaceScope.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\SpaceScope\SpaceScope.ico"; DestDir: "{app}"
Source: "..\SpaceScope\app\*"; DestDir: "{app}\app"; Flags: recursesubdirs createallsubdirs
Source: "..\SpaceScope\runtime\*"; DestDir: "{app}\runtime"; Flags: recursesubdirs createallsubdirs
Source: "..\..\..\LICENSE"; DestDir: "{app}"

[Languages]
Name: "korean"; MessagesFile: "compiler:Languages\Korean.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:TaskDesktop}"; GroupDescription: "{cm:AddShortcutsGroup}"; Flags: checkedonce
Name: "startmenuicon"; Description: "{cm:TaskStartMenu}"; GroupDescription: "{cm:AddShortcutsGroup}"; Flags: checkedonce

[Icons]
; 시작 메뉴 바로가기
Name: "{group}\{cm:StartMenuShortcutName}"; Filename: "{app}\SpaceScope.exe"; IconFilename: "{app}\SpaceScope.ico"; Tasks: startmenuicon
; 바탕화면 바로가기
Name: "{commondesktop}\{cm:DesktopShortcutName}"; Filename: "{app}\SpaceScope.exe"; IconFilename: "{app}\SpaceScope.ico"; Tasks: desktopicon

[Code]
procedure WriteDefaultLangIni;
var
  LangCode: string;
  DirPath: string;
  FilePath: string;
begin
  if ActiveLanguage = 'korean' then
    LangCode := 'ko'
  else
    LangCode := 'en';

  DirPath := ExpandConstant('{commonappdata}\SpaceScope');
  ForceDirectories(DirPath);

  FilePath := DirPath + '\lang.ini';

  if not FileExists(FilePath) then
    SaveStringToFile(FilePath, LangCode, False);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then begin
    WriteDefaultLangIni;
  end;
end;

[Run]
Filename: "{app}\SpaceScope.exe"; Description: "{cm:RunApp}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
