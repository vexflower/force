package engine;

import hardware.Display;
import hardware.Keyboard;
import lang.GeomMath;
import renderer.MasterRenderer;
import renderer.RenderState;
import renderer.SharedState;

public class GameEngine {

    private static volatile boolean running = true;
    private static final SharedState sharedState = new SharedState();

    public static void main(String... args) {
        System.out.println("Engine initialized from Bootloader.");

        // 1. Ignite the Hardware (Must be on main thread for macOS/Windows UI)
        Display.createDisplay(1280, 720);
        Display.setShowFPSTitle(true);
        MasterRenderer.setRenderer();

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
        float currentRotation = 0;
        long lastTime = System.nanoTime();
        final double nsPerTick = 1000000000.0 / 60.0; // Fixed 60 Ticks Per Second
        double delta = 0;

        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;

            // Catch up on missed physics ticks
            while (delta >= 1) {

                // 1. Grab the Back Buffer (The one the GPU isn't looking at)
                RenderState backBuffer = sharedState.getBackBuffer();

                // 2. Perform Game Logic & Physics
                currentRotation += GeomMath.toRadians(2); // 2 degrees per tick

                // 3. Write data to the Back Buffer
                backBuffer.spinningTriangleTransform.identity();
                backBuffer.spinningTriangleTransform.translate(0.0f, 0.0f, 0.5f);
                backBuffer.spinningTriangleTransform.rotateY(currentRotation);

                // 4. Publish the frame instantly
                sharedState.swap();

                delta--;
            }

            // Sleep slightly to prevent this thread from burning 100% of a CPU core
            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
    }
}