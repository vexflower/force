package engine;

import loader.MeshLoader;
import loader.TextureRegistry;
import renderer.RenderState;
import renderer.SharedState;
import ui.scene.Scene;
import hardware.Display;
import renderer.MasterRenderer;

public class GameEngine {

    private static volatile boolean running = true;
    private static final SharedState sharedState = new SharedState();
    // [NEW] The active scene!
    private static Scene currentScene;
    // Replace start(Scene) with parameterless start()
    public static void start() {
        Thread logicThread = new Thread(GameEngine::runLogicLoop, "Logic-Thread");
        logicThread.start();

        while (!Display.shouldDisplayClose() && running) {
            RenderState currentFrame = sharedState.getFrontBuffer();
            MasterRenderer.render(currentFrame);
            Display.updateDisplay();
        }

        // 3. Graceful Teardown
        running = false;
        try { logicThread.join(); } catch (InterruptedException e) { System.out.println(e.getMessage()); }

        // [THE FIX]: Force the CPU to wait until the GPU is completely idle
        org.lwjgl.vulkan.VK10.vkDeviceWaitIdle(hardware.Display.getDevice());

        // Fetch the actual container from the Display instead of a null variable
        ui.Container root = Display.getContentPane();
        if (root != null) {
            root.destroy(); // Safely nuke the FBOs!
        }

        MasterRenderer.destroy();
        MeshLoader.destroy();
        TextureRegistry.destroy();
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
                float deltaInSeconds = 1.0f / 60.0f;

                ui.Container root = Display.getContentPane();
                if (root != null) {
                    root.update(deltaInSeconds); // Ticks the whole tree

                    RenderState backBuffer = sharedState.getBackBuffer();
                    backBuffer.clear(); // <-- The new, clean reset!

                    // Harvest all data from the tree!
                    root.extract3DEntities(backBuffer);
                    root.extractUIData(backBuffer);

                    sharedState.swap();
                }
                delta--;
            }

            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
    }
}