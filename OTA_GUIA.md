# Guía de Actualizaciones OTA (GitHub Releases)

La aplicación **Pedidos Proveedores** incluye un auto-actualizador OTA nativo sobre **GitHub Releases**:
- Al abrirse (o desde el icono de actualización en *Proveedores*), consulta `https://api.github.com/repos/<usuario>/<repo>/releases/latest`.
- Si hay una versión más reciente que la instalada, muestra un banner **"Actualizar"**, descarga el `.apk` verificando su `SHA256` y lanza la instalación encima de la app actual **conservando todos los proveedores, catálogos y pedidos SQLite**.

## Configuración inicial (solo una vez)

### 1. Crear repositorio público en GitHub
Crea un repositorio público (por ejemplo `mrivero105-oss/pedidos-proveedores-android`) y sube este proyecto.

### 2. Crear una clave de firma fija (obligatorio para actualizar sin borrar datos)
Android exige que todas las versiones OTA estén firmadas con la **misma clave (keystore)** para poder actualizar encima sin desinstalar. Ejecuta el asistente incluido (crea el archivo fuera del repositorio en `%USERPROFILE%\PedidosProveedores-Signing\release.jks`):

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\generar_keystore_ota.ps1
```

El script genera `release.jks` y `KEYSTORE_BASE64.txt` fuera de Git. En GitHub → tu repositorio → **Settings → Secrets and variables → Actions → New repository secret**, crea estos 4 secretos:
- `KEYSTORE_BASE64`: el texto completo de `KEYSTORE_BASE64.txt`
- `KEYSTORE_PASSWORD`: la contraseña del keystore
- `KEY_ALIAS`: `pedidos-release`
- `KEY_PASSWORD`: la contraseña de la clave (la misma del keystore)

### 3. Instalar la primera versión firmada
Publica la primera release desde GitHub Actions (**Actions → Publicar APK OTA → Run workflow**, ej. `1.0.0`) o creando un tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Descarga e instala ese primer APK firmado (`pedidos-proveedores-1.0.0-release.apk`) en tu teléfono una única vez.

## Publicar futuras actualizaciones OTA
Cada vez que hagas cambios:

```bash
git tag v1.1.0
git push origin v1.1.0
```

GitHub Actions ejecutará las pruebas, compilará y firmará el APK con la misma clave y publicará la Release con su `SHA256`. Al abrir la app en tu teléfono aparecerá el aviso para actualizar en un toque.
