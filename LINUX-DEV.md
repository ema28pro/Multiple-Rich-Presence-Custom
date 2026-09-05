# Desarrollo en Linux

Este repositorio ha sido adaptado para permitir la compilación y ejecución directa en sistemas Linux.

## Requisitos Previos

Necesitarás tener instalado el Java Development Kit (JDK) versión 8 o superior. En Linux, puedes instalarlo ejecutando (por ejemplo en Ubuntu):

```bash
sudo apt update
sudo apt install default-jdk
```

## Compilación

Para compilar el proyecto en Linux, utiliza el script proporcionado. Aunque el código Java es multiplataforma, al momento de compilar se generará un archivo `.jar` separado llamado `DiscordPipeSocket-linux.jar`. Esto se hace para mantener separados los ejecutables.

1. Dale permisos de ejecución al script (solo la primera vez):
   ```bash
   chmod +x build.sh
   ```

2. Ejecuta el script:
   ```bash
   ./build.sh
   ```

Este script compila los archivos de la carpeta `src/` en una carpeta temporal y actualiza o crea el archivo `DiscordPipeSocket-linux.jar` automáticamente, haciendo un proceso limpio igual al de Windows.

## Ejecución

Una vez compilado, puedes correr la aplicación usando:

```bash
java -jar DiscordPipeSocket-linux.jar
```

Asegúrate de que el archivo `config.json` esté en el mismo directorio principal.
