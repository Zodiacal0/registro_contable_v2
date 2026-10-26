# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

Escritorio Windows (Java 25 + JavaFX, `Desktop/`) y Android (React Native, `Mobile/`, pendiente).

## Users

Una persona (el propio autor, desarrollador) que registra sus gastos personales. Usa la app
por igual para anotar gastos rápido a diario y para revisar en qué se le va el dinero en el mes.

## Product Purpose

Gestor de gastos personal, nativo y local. Éxito: registrar un gasto en segundos y entender el
mes de un vistazo (total, tendencia, categorías, comparación con el mes anterior).
Propósito secundario declarado: que el autor mejore su Java con un proyecto real.

## Positioning

Sin nube ni servicios externos: los datos viven en SQLite en cada dispositivo y se sincronizan
directamente entre escritorio y celular en la red local.

## Operating Context

Uso personal en el escritorio de Windows y en el celular Android. Interfaz en español.

## Capabilities and Constraints

- Gastos con descripción, monto (2 decimales), fecha y categoría opcional; edición y borrado lógico.
- Categorías con color guardado en la base (se sincroniza entre dispositivos).
- Resumen mensual: total, variación vs mes anterior, promedio diario, mayor gasto, desglose por categoría.
- Pendiente: sincronización LAN (Desktop ↔ Mobile) y app Mobile.
- Moneda: el usuario usa quetzales y dólares; el soporte multimoneda está **por decidir** (hoy los montos no llevan símbolo).
- No hay ingresos, presupuestos ni cuentas: solo gastos.

## Brand Commitments

- Paleta obligatoria dada por el usuario: #17151C, #292531, #45404F, #716879, #AAA2B3, #E2DDE7 (gris violáceo, de oscuro a claro). No introducir colores que rompan ese tono.
- Referencias de composición explícitas: Apple (simplicidad), Binance (lectura tipo wallet), Steam (paneles en capas).
- Debe sentirse app de escritorio, no página web; ni vacía ni recargada.

## Evidence on Hand

No hay datos reales todavía; las capturas y la demo usan datos de ejemplo sintéticos
(`Desktop/src/test/java/com/registrogastos/CapturaPantallas.java`).

## Product Principles

1. Registrar es inmediato: la acción de nuevo gasto está siempre a un clic o un atajo.
2. El mes se entiende de un vistazo, sin navegar.
3. Los datos son del usuario y no salen de sus dispositivos.
4. Cada número tiene contexto (comparación, proporción o tendencia), nunca una cifra suelta.
