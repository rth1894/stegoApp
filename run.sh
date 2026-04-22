#!/bin/bash

echo ""
echo " ================================"
echo "  STEGOAPP LAUNCHER"
echo " ================================"
echo ""
echo "  [1]  Normal Start     (compile & run)"
echo "  [2]  Fresh Start      (clean + recompile & run)"
echo "  [3]  Build JAR        (create StegoApp.jar for sharing)"
echo ""
read -p " Choice: " choice

# ── JavaFX path ───────────────────────────────────────────────────
FX="lib/javafx-sdk/lib"
MODS="javafx.controls,javafx.fxml,javafx.web"
SOURCES="src/module-info.java src/core/Main.java src/ui/UIBridge.java src/image/ImageHandler.java src/encryption/AESEncryption.java src/steganography/LSBEncoder.java src/steganography/LSBDecoder.java src/utils/CapacityCalculator.java"

# ── Option 2: Fresh Start ─────────────────────────────────────────
if [ "$choice" == "2" ]; then
    echo ""
    echo " Cleaning out/ folder..."
    rm -rf out
    mkdir out
    echo " Done. Fresh build starting..."
    echo ""
fi

# ── Option 3: Build JAR ───────────────────────────────────────────
if [ "$choice" == "3" ]; then
    echo ""
    echo " Cleaning and rebuilding for JAR..."
    rm -rf out
    mkdir out

    echo " Compiling..."
    javac --module-path $FX \
          --add-modules $MODS \
          -d out $SOURCES

    if [ $? -ne 0 ]; then
        echo " BUILD FAILED."
        exit 1
    fi

    echo " Copying resources..."
    cp -r resources/* out/

    echo " Creating manifest..."
    echo "Main-Class: core.Main" > manifest.txt

    echo " Packaging StegoApp.jar..."
    jar --create --file StegoApp.jar --manifest=manifest.txt -C out .

    if [ $? -ne 0 ]; then
        echo " JAR BUILD FAILED."
        exit 1
    fi

    rm manifest.txt

    echo ""
    echo " ================================"
    echo "  StegoApp.jar created!"
    echo " ================================"
    echo ""
    echo " To run on any Linux machine:"
    echo ""
    echo " java --module-path path/to/javafx-sdk/lib \\"
    echo "      --add-modules javafx.controls,javafx.fxml,javafx.web \\"
    echo "      -jar StegoApp.jar"
    echo ""
    exit 0
fi

# ── Option 1 and 2: Compile and Run ──────────────────────────────
echo " Compiling..."
javac --module-path $FX \
      --add-modules $MODS \
      -d out $SOURCES

if [ $? -ne 0 ]; then
    echo ""
    echo " BUILD FAILED. Check errors above."
    echo ""
    exit 1
fi

echo " Compile successful. Launching..."
echo ""
java --module-path $FX \
     --add-modules $MODS \
     -cp "out:resources" \
     core.Main
