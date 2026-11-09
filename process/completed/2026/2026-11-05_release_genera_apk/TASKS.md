# Tareas

- [x] Reproducir: `mise release` terminaba con error y el APK no aparecía en
      `release/`.
- [x] Localizar el primer bloqueo: refrescar la fecha del ejecutable de jpackage
      falla porque el fichero llega con el atributo de solo lectura.
- [x] Segundo bloqueo: `Get-AuthenticodeSignature` no carga el módulo
      `Microsoft.PowerShell.Security` en este equipo.
- [x] Causa de fondo: el proceso hereda el `PSModulePath` de PowerShell 7, así
      que Windows PowerShell carga los módulos de PS7 y sus cmdlets no existen
      en 5.1.
- [x] Sancionar el `PSModulePath` al inicio de los scripts de release.
- [x] Verificación de firma con lectura directa del certificado por .NET como
      respaldo.
- [x] Compilar cada plataforma por separado: un fallo en Windows no deja el
      APK sin generar.
- [x] Comprobar que `mise release` termina bien y genera los tres artefactos.
