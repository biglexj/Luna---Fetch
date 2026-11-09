# Conexión de la extensión con la app de escritorio: fallo silencioso y diagnóstico

Autorización: solicitud de Biglex del 2026-11-05 («a pesar de que está abierto
hay una desconexión»).

- Averiguar por qué la extensión responde «Luna Fetch no está abierto en tu PC»
  cuando la aplicación está en marcha.
- Corregir la lectura del cuerpo de la petición: se leía por caracteres cuando
  `Content-Length` cuenta bytes.
- Evitar que el servidor se trague un fallo y deje la conexión muerta sin
  responder.
- Mostrar el motivo real del fallo en la extensión y añadir un endpoint de
  estado para saber si la app responde.

La aplicación instalada y la instancia de desarrollo son distintas. No publicar
ni atribuir aceptación humana a las comprobaciones técnicas.
