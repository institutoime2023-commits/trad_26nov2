# PRUEBA AISLADA DEMUCS — XIAOMI REDMI 12C (4 GB RAM)

Aplicación de diagnóstico independiente. NO modifica ni reemplaza el Traductor V0.6.4.

## Objetivo
Medir en el Xiaomi Redmi 12C la separación de la pista vocal de una canción capturada por el micrófono, antes de decidir si Demucs puede incorporarse como modo nuevo al traductor.

## Configuración inicial conservadora
- Motor: jPaauwe/demucs-android (C++), licencia MIT.
- Pesos: HTDemucs-6s, 55 MB, SHA-256 verificado.
- Entrada: micrófono, 44,1 kHz, mono PCM convertido a float32.
- Muestra: 10 segundos.
- Salida: solo `vocals.wav`.
- Segmentos simultáneos: 1 (el autor estima unos 0,6 GB de memoria de trabajo por hilo).
- Hilos de cálculo: 4 para limitar la carga térmica inicial.
- Primera separación: CPU, Vulkan desactivado.
- Muestra PSS de memoria en callbacks y medición del tiempo total.

## Compilación
Subir el contenido del ZIP a un repositorio independiente, rama `main`. GitHub Actions descargará el motor y Eigen como dependencia abierta y generará el APK de prueba.

## Prueba
1. Cerrar otras aplicaciones y reiniciar el teléfono.
2. Instalar APK y conceder permiso de micrófono.
3. Descargar modelo (55 MB); la app verifica SHA-256.
4. Poner el teléfono cerca del parlante, sin saturar el micrófono.
5. Grabar una canción durante 10 segundos.
6. Separar voz con CPU, un hilo y solo salida `vocals`.
7. Escuchar la voz aislada y anotar el tiempo total y PSS máxima observada.
8. Solo si CPU pasa, consultar Vulkan y preparar otra prueba de GPU.

## Criterio de seguridad
Si Android cierra la app, aparece `OutOfMemoryError`, el teléfono se calienta excesivamente o la separación tarda mucho más que el audio, no integrar esta configuración al traductor. La PSS observada en callbacks no es un registro continuo del pico absoluto de memoria.

## Licencias
Motor y pesos Demucs: MIT. Eigen: MPL-2.0. Conservar avisos de licencia al redistribuir.
