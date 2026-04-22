package core;

// ─────────────────────────────────────────────────────────────────────────────
// HOW TO RUN:
// From StegoApp/ folder:
//
// Compile:
// javac --module-path lib/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml,javafx.web -d out src/module-info.java src/core/Main.java src/ui/UIBridge.java src/image/ImageHandler.java src/encryption/AESEncryption.java src/steganography/LSBEncoder.java src/steganography/LSBDecoder.java src/utils/CapacityCalculator.java
//
// Run:
// java --module-path lib/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml,javafx.web -cp "out;resources" core.Main
// ─────────────────────────────────────────────────────────────────────────────

import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import ui.UIBridge;

import java.net.URL;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {

        // Create WebView and get its engine
        WebView   webView   = new WebView();
        WebEngine webEngine = webView.getEngine();

        // Create the bridge — needs webEngine to call back into JS
        UIBridge uiBridge = new UIBridge(webEngine);

        // ── Inject bridge AFTER page finishes loading ─────────────────────
        // CRITICAL: must wait for SUCCEEDED state — DOM doesn't exist before this
        webEngine.getLoadWorker().stateProperty().addListener(
            (observable, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {

                    // Inject UIBridge as "javaConnector" into JS window object
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("javaConnector", uiBridge);

                    System.out.println("✅ Java ↔ JS bridge injected successfully.");
                }
            }
        );

        // Load the HTML UI
        URL htmlPage = getClass().getResource("/ui/index.html");
        if (htmlPage == null) {
            System.err.println("ERROR: Could not find /ui/index.html");
            return;
        }
        webEngine.load(htmlPage.toExternalForm());

        // Window setup
        StackPane root  = new StackPane(webView);
        Scene     scene = new Scene(root, 1100, 720);

        primaryStage.setTitle("Digital Steganography");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.show();

        System.out.println("✅ JavaFX window launched.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
