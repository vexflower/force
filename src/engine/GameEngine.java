package engine;

import loader.MeshLoader;
import loader.TextureRegistry;
import ui.scene.Scene;
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

    // Replace public static void main(String... args) with this:
    public static void start(Scene initialScene) {
        currentScene = initialScene;

        // 1. Spawn the Logic/Physics Thread
        Thread logicThread = new Thread(GameEngine::runLogicLoop, "Logic-Thread");
        logicThread.start();

        // 2. The Unlocked Render Loop
        while (!Display.shouldDisplayClose() && running) {
            if (hardware.Keyboard.isKeyDown(hardware.Keyboard.ZERO)) {
                System.out.println("F5 Pressed: Engine signaling Bootloader for Hotswap...");
                running = false;
            }

            // Grab the latest snapshot instantly. No locks.
            RenderState currentFrame = sharedState.getFrontBuffer();
            MasterRenderer.render(currentFrame);

            Display.updateDisplay();
        }

        // 3. Graceful Teardown
        running = false;
        try { logicThread.join(); } catch (InterruptedException e) { System.out.println(e.getMessage()); }

        // [THE FIX]: Force the CPU to wait until the GPU is completely idle
        org.lwjgl.vulkan.VK10.vkDeviceWaitIdle(hardware.Display.getDevice());

        currentScene.destroy(); // Now it is safe to nuke the FBOs!

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
                // [CHANGED] We delegate all logic to the Scene!
                float deltaInSeconds = 1.0f / 60.0f;
                currentScene.engineUpdate(deltaInSeconds);

                // [NEW: Snapshot the state for the GPU!]
                RenderState backBuffer = sharedState.getBackBuffer();
                backBuffer.entityCount = currentScene.activeEntities.size();

                for (int i = 0; i < backBuffer.entityCount; i++) {
                    int eId = currentScene.activeEntities.get(i);
                    backBuffer.meshIds[i] = environment.RendererManager.meshIds.get(eId);
                    backBuffer.textureIds[i] = environment.RendererManager.diffuseTextureIds.get(eId);

                    // Copy the 16 floats directly to avoid GC allocations
                    for (int f = 0; f < 16; f++) {
                        backBuffer.transforms[(i * 16) + f] = environment.RendererManager.transforms.get((eId * 16) + f);
                    }
                }

                // [NEW] Extract the FBO UI data!
                currentScene.extractUIData(backBuffer);

                // We still tell the sharedState to swap so the Render Thread grabs the newest data
                sharedState.swap();
                delta--;
            }

            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
    }
}