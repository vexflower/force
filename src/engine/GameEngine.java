package engine;

import hardware.Display;
import renderer.MasterRenderer;

/**
 * The main entry point for the application.
 */
public class GameEngine {

    public static void main(String[] args) {
        System.out.println("Starting Engine Initialization...");

        // 1. Ignite the Hardware (This triggers our Vulkan diagnostics!)
        Display.createDisplay(1280, 720);
        MasterRenderer.setRenderer();

        // 2. The Core Engine Loop
        System.out.println("Entering Main Game Loop...");
        while (!Display.shouldDisplayClose()) {
            Display.updateDisplay();
        }

        MasterRenderer.destroy();
        // 3. Clean Shutdown
        Display.closeDisplay();
        System.out.println("Engine shut down successfully.");
    }
}