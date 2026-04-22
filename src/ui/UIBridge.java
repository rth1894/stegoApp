package ui;

import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import image.ImageHandler;
import encryption.AESEncryption;
import steganography.LSBEncoder;
import steganography.LSBDecoder;
import utils.CapacityCalculator;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * UIBridge
 * Responsibility: The ONLY class the JavaScript frontend talks to.
 * - All public methods are callable from JS via JSObject bridge
 * - Orchestrates all backend modules in correct order
 * - Sends results back to JS via WebEngine.executeScript()
 * - All JS callbacks wrapped in Platform.runLater() for thread safety
 *
 * ENCODE FLOW:
 *   JS → openFileChooser("encode")
 *   JS → encodeMessage(imagePath, message, password)
 *      → ImageHandler.loadImage()
 *      → AESEncryption.encrypt()
 *      → LSBEncoder.hasCapacity() + embedData()
 *      → ImageHandler.saveImage()
 *      → JS: displayEncodeResult(outputPath)
 *
 * DECODE FLOW:
 *   JS → openFileChooser("decode")
 *   JS → decodeMessage(imagePath, password)
 *      → ImageHandler.loadImage()
 *      → LSBDecoder.extractData()
 *      → AESEncryption.decrypt()
 *      → JS: displayDecodeResult(message)
 */
public class UIBridge {

    private final WebEngine        webEngine;
    private final ImageHandler     imageHandler;
    private final AESEncryption    aesEncryption;
    private final LSBEncoder       lsbEncoder;
    private final LSBDecoder       lsbDecoder;
    private final CapacityCalculator capacityCalculator;

    // Stores absolute file paths selected via FileChooser
    // JS cannot access real file paths — Java stores them here
    private String encodeImagePath   = null;
    private String decodeImagePath   = null;
    private String analysisImagePath = null;

    /**
     * Constructor — receives WebEngine so bridge can call back into JS.
     * @param webEngine the active WebEngine from Main.java
     */
    public UIBridge(WebEngine webEngine) {
        this.webEngine          = webEngine;
        this.imageHandler       = new ImageHandler();
        this.aesEncryption      = new AESEncryption();
        this.lsbEncoder         = new LSBEncoder();
        this.lsbDecoder         = new LSBDecoder();
        this.capacityCalculator = new CapacityCalculator();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FILE CHOOSER
    // Called from JS when user clicks an upload zone.
    // Opens a native Java FileChooser — gets real absolute path.
    // Passes path back to JS and triggers capacity calculation if encoding.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens a native FileChooser dialog and stores the selected file path.
     * Called from JS: window.javaConnector.openFileChooser("encode")
     * @param context "encode", "decode", or "analysis"
     */
    public void openFileChooser(String context) {
        Platform.runLater(() -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Image");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                    "Image Files", "*.png", "*.jpg", "*.jpeg"
                )
            );

            File selected = fileChooser.showOpenDialog(new Stage());

            if (selected == null) {
                // User cancelled — do nothing
                return;
            }

            String absolutePath = selected.getAbsolutePath();
            // Escape backslashes for Windows paths in JS strings
            String safePath = absolutePath.replace("\\", "\\\\");

            switch (context) {
                case "encode":
                    encodeImagePath = absolutePath;
                    callJS("onImageSelected", safePath + "', '" + context);
                    getCapacity(absolutePath);
                    break;
                case "decode":
                    decodeImagePath = absolutePath;
                    callJS("onImageSelected", safePath + "', '" + context);
                    break;
                case "analysis":
                    analysisImagePath = absolutePath;
                    callJS("onImageSelected", safePath + "', '" + context);
                    runAnalysis(absolutePath);
                    break;
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENCODE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full encode pipeline: load → encrypt → embed → save → notify JS.
     * Called from JS: window.javaConnector.encodeMessage(message, password)
     * Note: imagePath is stored by openFileChooser — not passed from JS
     * @param message  plain text secret message
     * @param password user password for AES encryption
     */
    public void encodeMessage(String message, String password) {
        Platform.runLater(() -> {

            // ── Validation ────────────────────────────────────────────────
            if (encodeImagePath == null) {
                callJS("displayError", "encode', 'No image selected.");
                return;
            }
            if (message == null || message.trim().isEmpty()) {
                callJS("displayError", "encode', 'Message cannot be empty.");
                return;
            }
            if (password == null || password.isEmpty()) {
                callJS("displayError", "encode', 'Password cannot be empty.");
                return;
            }

            // ── Step 1: Load image ────────────────────────────────────────
            BufferedImage coverImage = imageHandler.loadImage(encodeImagePath);
            if (coverImage == null) {
                callJS("displayError", "encode', 'Failed to load image.");
                return;
            }

            // ── Step 2: Encrypt message ───────────────────────────────────
            byte[] encryptedData = aesEncryption.encrypt(message, password);
            if (encryptedData == null) {
                callJS("displayError", "encode', 'Encryption failed.");
                return;
            }

            // ── Step 3: Check capacity AFTER encryption ───────────────────
            // Encrypted bytes are larger than original message — check fits
            if (!lsbEncoder.hasCapacity(coverImage, encryptedData)) {
                int maxChars = capacityCalculator.calculateMaxChars(coverImage);
                callJS("displayError", "encode', 'Message too large for this image. "
                    + "Max capacity: " + maxChars + " chars.");
                return;
            }

            // ── Step 4: Embed encrypted data into image ───────────────────
            BufferedImage stegoImage = lsbEncoder.embedData(coverImage, encryptedData);
            if (stegoImage == null) {
                callJS("displayError", "encode', 'Embedding failed.");
                return;
            }

            // ── Step 5: Save stego-image to disk ──────────────────────────
            String outputPath = imageHandler.generateOutputPath(encodeImagePath);
            boolean saved     = imageHandler.saveImage(stegoImage, outputPath);
            if (!saved) {
                callJS("displayError", "encode', 'Failed to save stego-image.");
                return;
            }

            // ── Step 6: Notify JS of success ──────────────────────────────
            String safePath = outputPath.replace("\\", "\\\\");
            callJS("displayEncodeResult", safePath);
            System.out.println("UIBridge: Encode complete → " + outputPath);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DECODE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Full decode pipeline: load → extract → decrypt → notify JS.
     * Called from JS: window.javaConnector.decodeMessage(password)
     * Note: imagePath is stored by openFileChooser — not passed from JS
     * @param password user password for AES decryption
     */
    public void decodeMessage(String password) {
        Platform.runLater(() -> {

            // ── Validation ────────────────────────────────────────────────
            if (decodeImagePath == null) {
                callJS("displayError", "decode', 'No image selected.");
                return;
            }
            if (password == null || password.isEmpty()) {
                callJS("displayError", "decode', 'Password cannot be empty.");
                return;
            }

            // ── Step 1: Load stego-image ──────────────────────────────────
            BufferedImage stegoImage = imageHandler.loadImage(decodeImagePath);
            if (stegoImage == null) {
                callJS("displayError", "decode', 'Failed to load image.");
                return;
            }

            // ── Step 2: Extract encrypted bytes from image ────────────────
            byte[] extractedData = lsbDecoder.extractData(stegoImage);
            if (extractedData == null) {
                callJS("displayError", "decode', 'No hidden data found in image.");
                return;
            }

            // ── Step 3: Decrypt extracted bytes ───────────────────────────
            String message = aesEncryption.decrypt(extractedData, password);
            if (message == null) {
                // null = wrong password (BadPaddingException caught in AESEncryption)
                callJS("displayError", "decode', 'Wrong password or corrupted data.");
                return;
            }

            // ── Step 4: Send decrypted message to JS ──────────────────────
            // Escape single quotes in message to avoid breaking JS string
            String safeMessage = message.replace("'", "\\'")
                                        .replace("\n", "\\n")
                                        .replace("\r", "");
            callJS("displayDecodeResult", safeMessage);
            System.out.println("UIBridge: Decode complete.");
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CAPACITY
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates image capacity and sends result to JS.
     * Called internally after file selection for encode context.
     * Also callable from JS: window.javaConnector.getCapacity(imagePath)
     * @param imagePath absolute path to the selected image
     */
    public void getCapacity(String imagePath) {
        Platform.runLater(() -> {
            BufferedImage image = imageHandler.loadImage(imagePath);
            if (image == null) {
                callJS("setCapacity", "0");
                return;
            }

            int maxChars = capacityCalculator.calculateMaxChars(image);
            callJS("setCapacity", String.valueOf(maxChars));

            System.out.println(capacityCalculator.getCapacitySummary(image));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANALYSIS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs image analysis and sends stats to JS.
     * Called internally after file selection for analysis context.
     * @param imagePath absolute path to the image
     */
    public void runAnalysis(String imagePath) {
        Platform.runLater(() -> {
            BufferedImage image = imageHandler.loadImage(imagePath);
            if (image == null) {
                callJS("displayError", "analysis', 'Failed to load image.");
                return;
            }

            int width    = image.getWidth();
            int height   = image.getHeight();
            int pixels   = width * height;
            int maxChars = capacityCalculator.calculateMaxChars(image);

            // Send each stat individually to JS
            callJS("setAnalysisStat", "stat-width', '"    + width);
            callJS("setAnalysisStat", "stat-height', '"   + height);
            callJS("setAnalysisStat", "stat-pixels', '"   + pixels);
            callJS("setAnalysisStat", "stat-capacity', '" + maxChars);
        });
    }

    /**
     * Clears the stored image path for the given context.
     * Called from JS when user clicks Clear button.
     * @param context "encode", "decode", or "analysis"
     */
    public void clearContext(String context) {
        switch (context) {
            case "encode":
                encodeImagePath = null;
                break;
            case "decode":
                decodeImagePath = null;
                break;
            case "analysis":
                analysisImagePath = null;
                break;
        }
        System.out.println("UIBridge: Cleared context — " + context);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JS CALLBACK HELPER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes a JavaScript function call via WebEngine.
     * All Java → JS communication goes through this method.
     *
     * Usage: callJS("functionName", "argument")
     * Executes: functionName('argument')
     *
     * For multi-argument calls, data already contains ', ' separators.
     * Example: callJS("displayError", "encode', 'message text")
     * Executes: displayError('encode', 'message text')
     *
     * @param jsFunction the JS function name to call
     * @param data       the argument(s) to pass
     */
    private void callJS(String jsFunction, String data) {
        Platform.runLater(() -> {
            try {
                String script = jsFunction + "('" + data + "')";
                webEngine.executeScript(script);
            } catch (Exception e) {
                System.err.println("UIBridge: JS call failed ["
                    + jsFunction + "] — " + e.getMessage());
            }
        });
    }
}
