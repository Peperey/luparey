# luparey

App Android de lupa y microscopio: usa la cámara trasera con zoom ajustable y linterna.

## Compilar en GitHub
Cada `push` ejecuta el workflow de `.github/workflows/build.yml`.
Al terminar, descarga el APK en la pestaña **Actions** > última ejecución > **Artifacts**.

## Subir desde Termux
```
pkg install git
git init
git add .
git commit -m "Primera versión de luparey"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/luparey.git
git push -u origin main
```
