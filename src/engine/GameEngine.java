package engine;

import environment.Scene;
import hardware.Display;
import hardware.Keyboard;
import renderer.MasterRenderer;
import renderer.RenderState;
import renderer.SharedState;

public class GameEngine {

    private static volatile boolean running = true;
    private static final SharedState sharedState = new SharedState();
    // [NEW] The active scene!
    private static Scene currentScene;

    public static void main(String... args) {
        System.out.println("Engine initialized from Bootloader.");

        // 1. Ignite the Hardware (Must be on main thread for macOS/Windows UI)
        Display.createDisplay(1280, 720);
        Display.setShowFPSTitle(true);
        MasterRenderer.setRenderer();

        // [CHANGED] Initialize the Scene AFTER the renderer is ready (so we have a command pool)
        currentScene = new environment.WorldScene();
        currentScene.init(MasterRenderer.getCommandPool());

        // 2. Spawn the Logic/Physics Thread
        Thread logicThread = new Thread(GameEngine::runLogicLoop, "Logic-Thread");
        logicThread.start();

        // 3. The Unlocked Render Loop (Runs as fast as your GPU allows)
        while (!Display.shouldDisplayClose() && running) {

            // Listen for hotswap secret key
            if (Keyboard.isKeyDown(Keyboard.ZERO)) {
                System.out.println("F5 Pressed: Engine signaling Bootloader for Hotswap...");
                running = false;
            }

            // Grab the latest physics snapshot instantly. No locks.
            RenderState currentFrame = sharedState.getFrontBuffer();

            // Pass the extracted data to Vulkan
            MasterRenderer.render(currentFrame.spinningTriangleTransform);

            Display.updateDisplay();
        }

        // 4. Graceful Teardown
        running = false;
        try {
            logicThread.join(); // Wait for physics to safely shut down
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }

        MasterRenderer.destroy();
        Display.closeDisplay();
    }


    /**
     * This is your new GAME LOOP. It runs entirely independent of the framerate.
     */
    private static void runLogicLoop() {
        long lastTime = System.nanoTime();
        final double nsPerTick = 1000000000.0 / 60.0;
        double delta = 0;

        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;

            while (delta >= 1) {
                // [CHANGED] We delegate all logic to the Scene!
                float deltaInSeconds = 1.0f / 60.0f;
                currentScene.update(deltaInSeconds);

                // We still tell the sharedState to swap so the Render Thread grabs the newest data
                sharedState.swap();
                delta--;
            }

            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
    }
}