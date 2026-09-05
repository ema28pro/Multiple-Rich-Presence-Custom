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

echo "[2/3] Updating JAR with new classes..."
if [ ! -f "DiscordPipeSocket-linux.jar" ]; then
    cp "DiscordPipeSocket.jar" "DiscordPipeSocket-linux.jar"
    echo "    Created base: DiscordPipeSocket-linux.jar"
fi

jar uf "DiscordPipeSocket-linux.jar" -C build br/com/brforgers/armelin/dps/

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to update JAR."
    exit 1
fi

echo "[3/3] Verifying..."
jar tf "DiscordPipeSocket-linux.jar" | grep -i "Config.class\|SourceManager"

echo ""
echo "============================================"
echo " Build successful!"
echo " JAR updated: DiscordPipeSocket-linux.jar"
echo "============================================"
echo "Make sure config.json is next to the JAR with your Discord Client ID."
