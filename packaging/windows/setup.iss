; ============================================================================
; setup.iss - Instalador de Windows de Luna Fetch (Biglex Modern 2.0)
; ----------------------------------------------------------------------------
; Basado en la plantilla compartida Scripts\tools\inno\BiglexModern.iss.
; Copia autocontenida: este repositorio no incluye nada de la carpeta
; compartida. BiglexModernUI.iss es la misma copia de la plantilla.
;
; version.iss lo genera/actualiza el script de build con la version real:
;   #define MyAppVersion "1.2.3"
;
; Compilar (lo hace scripts\packaging\Build-Installer.ps1):
;   & "D:\Proyectos\biglexj\Scripts\tools\InnoSetup\ISCC.exe" setup.iss
; ============================================================================

#include "version.iss"
#include "identity.iss"

[Setup]
; --- IDENTIDAD DE ACTUALIZACION: unica y estable por app -------------------
; GUID propio de Inno Setup. NO es el UpgradeCode del MSI: Windows Installer
; no reconoce actualizaciones entre MSI y EXE. Ver MIGRATION.md.
; Dobles llaves: escaparan la llave de apertura del GUID, que InnoSetup
; interpretaria como una referencia a constante. La llave de cierre ya viene
; incluida en el valor de MyAppId, por eso aqui solo se abre.
AppId={{#MyAppId}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher=Biglex
AppPublisherURL=https://biglexj.com
AppSupportURL=https://biglexj.com
DefaultDirName={localappdata}\Programs\{#MyAppDirectory}
DefaultGroupName={#MyAppName}
; --- Pantallas: solo la nuestra + progreso + exito --------------------------
DisableWelcomePage=yes
DisableDirPage=yes
DisableProgramGroupPage=yes
DisableReadyPage=yes
OutputDir=Output
OutputBaseFilename={#MyAppDirectory}-Windows-{#MyAppVersion}
SetupIconFile={#MyAppIcon}
UninstallDisplayIcon={app}\{#MyAppExe}
UninstallDisplayName={#MyAppName}
Compression=lzma2/max
SolidCompression=yes
ShowLanguageDialog=no
; --- Identidad visual moderna + tema claro/oscuro automatico (IS 6.6+) ---
WizardStyle=modern {#MyAppTheme}
#if MyAppTheme == "dark"
WizardBackColor=#15191C
WizardImageBackColor=#15191C
#else
WizardBackColor=#F7F9FA
WizardImageBackColor=clWhite
#endif
WizardBackColorDynamicDark=#15191C
WizardImageBackColorDynamicDark=#15191C
; --- Instalacion por usuario (sin privilegios de administrador) -------------
; PrivilegesRequiredOverridesAllowed se deja vacio a proposito: abrir el
; dialogo "Seleccion del Modo de Instalacion" anade una pantalla extra.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; --- Comportamiento --------------------------------------------------------
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
VersionInfoDescription={#MyAppName} instalador
VersionInfoProductName={#MyAppName}
VersionInfoCompany=Biglex

[Languages]
Name: "spanish"; MessagesFile: "compiler:Languages\Spanish.isl"

[Messages]
spanish.FinishedHeadingLabel=Instalación completada
spanish.FinishedLabel={#MyAppName} se instaló correctamente.
spanish.FinishedLabelNoIcons={#MyAppName} se instaló correctamente.
spanish.ClickFinish=Pulsa Finalizar para cerrar el instalador.

[CustomMessages]
spanish.RunApp=Ejecutar {#MyAppName}

[Files]
; Distribucion real de la app (app-image de jpackage), preparada en staging
; por el script de build.
Source: "{#MyAppPayloadDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
; Icono oficial transparente: se incrusta para la vista previa de la
; cabecera y el contenedor del logo. dontcopy evita instalarlo como payload.
Source: "{#MyAppIcon}"; Flags: dontcopy

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"
; El MSI anterior dejaba tambien acceso directo en el escritorio: se conserva
; el comportamiento sin anadir ninguna pantalla al asistente.
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExe}"

[Run]
; Lanza la app via el shell del escritorio: heredar el contexto del instalador
; hace que el proceso falle con 0xc00004bc. explorer.exe vive en {win}, no en {sys}.
Filename: "{win}\explorer.exe"; Parameters: """{app}\{#MyAppExe}"""; Description: "{cm:RunApp}"; Flags: nowait postinstall skipifsilent

; Presentacion Biglex Modern 2.0 (copia local de la plantilla compartida).
#include "BiglexModernUI.iss"

[Code]
{ Nota de transicion: el actualizador automatico de la app lanza el instalador
  descargado con /passive (convencion del bootstrapper WiX Burn). Inno Setup
  ignora ese parametro y abre el asistente. Inno no expone al script la ruta
  del EXE original (ParamStr(0) es la copia temporal interna), asi que no se
  puede relanzar a si mismo de forma fiable. El ajuste corresponde al
  actualizador de la app; ver MIGRATION.md. }
procedure InitializeWizard();
begin
  ModernInitialize;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  ModernPageChanged(CurPageID);
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := ModernNextButtonClick(CurPageID);
end;

[Code]
procedure CancelButtonClick(CurPageID: Integer; var Cancel, Confirm: Boolean);
begin
  ModernCancelButtonClick(CurPageID, Cancel, Confirm);
end;
