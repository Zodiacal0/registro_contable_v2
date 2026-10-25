# Registro de Gastos

Gestor de gastos nativo con dos clientes que comparten datos directamente, sin
servicios externos.

| Carpeta | Qué es | Stack |
|---|---|---|
| `Desktop/` | App de escritorio | Java 25, SQLite (sqlite-jdbc), Maven Wrapper; UI en JavaFX (próxima fase) |
| `Mobile/` | App Android (APK) | React Native + SQLite (pendiente) |

## Sincronización (plan)

Dispositivo a dispositivo en la misma red, sin nube:

1. El Desktop levanta un servidor HTTP pequeño en la LAN (`com.sun.net.httpserver`, viene en el JDK).
2. El celular se empareja escaneando un QR con IP, puerto y un token.
3. Cada lado manda los registros con `actualizado_en` posterior a la última sync; los
   conflictos se resuelven con last-write-wins y los borrados viajan como `eliminado = 1`.

El esquema ya está preparado para esto (UUID, timestamps, borrado lógico). Ver
`Desktop/docs/01-nucleo-y-persistencia.md`.

## Línea gráfica

Simplicidad de Apple + lectura de wallet de Binance (saldos, cifras, verde/rojo) +
paneles oscuros en capas de Steam.
