#!/bin/bash
echo "============================================"
echo " Building Discord RPC Bridge (Linux Script)"
echo "============================================"
echo ""

if ! command -v javac &> /dev/null; then
    echo "ERROR: javac not found. Install JDK 8+ (e.g., sudo apt install default-jdk)."
    exit 1
fi

rm -rf build
mkdir build

echo "[1/3] Compiling Java sources..."
javac -cp "DiscordPipeSocket.jar" -d "build" --release 8 src/br/com/brforgers/armelin/dps/*.java

if [ $? -ne 0 ]; then
    echo "ERROR: Compilation failed."
    exit 1
fi

echo "[2/3] Updating JARs with new classes..."
if [ ! -f "DiscordPipeSocket-linux.jar" ]; then
    cp "DiscordPipeSocket.jar" "DiscordPipeSocket-linux.jar"
    echo "    Created base: DiscordPipeSocket-linux.jar"
fi

jar uf "DiscordPipeSocket-linux.jar" -C build br/com/brforgers/armelin/dps/
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to update DiscordPipeSocket-linux.jar."
    exit 1
fi

cp -f "DiscordPipeSocket-linux.jar" "DiscordCustomRPC-linux.jar"
jar ufe "DiscordCustomRPC-linux.jar" br.com.brforgers.armelin.dps.CustomPipeSocket -C build br/com/brforgers/armelin/dps/
jar uf "DiscordCustomRPC-linux.jar" custom-status/index.html custom-status/app.js custom-status/style.css
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to create DiscordCustomRPC-linux.jar."
    exit 1
fi

echo "[3/3] Verifying..."
jar tf "DiscordPipeSocket-linux.jar" | grep -i "Config.class\|SourceManager\|CustomPipeSocket"

echo ""
echo "============================================"
echo " Build successful!"
echo " Multi-Source JAR: DiscordPipeSocket-linux.jar"
echo " Custom-Only JAR:  DiscordCustomRPC-linux.jar"
echo "============================================"
echo "Make sure config.json is next to the JAR with your Discord Client ID."
