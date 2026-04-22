@echo off
echo Compiling...
javac --module-path lib/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml,javafx.web -d out src/module-info.java src/core/Main.java src/ui/UIBridge.java src/image/ImageHandler.java src/encryption/AESEncryption.java src/steganography/LSBEncoder.java src/steganography/LSBDecoder.java src/utils/CapacityCalculator.java

if %errorlevel% neq 0 (
    echo.
    echo BUILD FAILED. Check errors above.
    pause
    exit /b 1
)

echo Compile successful. Launching app...
java --module-path lib/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml,javafx.web -cp "out;resources" core.Main
