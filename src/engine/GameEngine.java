package engine;

import hardware.VulkanContext;
import hardware.Window;
import loader.MeshLoader;
import loader.TextureRegistry;
import renderer.RenderState;
import renderer.SharedState;
import ui.scene.Scene;
import renderer.MasterRenderer;

public class GameEngine {

    private static volatile boolean running = true;
    private static final SharedState sharedState = new SharedState();

    public static void start() {
        Thread logicThread = new Thread(GameEngine::runLogicLoop, "Logic-Thread");
        logicThread.start();

        while (!Window.shouldClose() && running) {
            RenderState currentFrame = sharedState.getFrontBuffer();
            MasterRenderer.render(currentFrame);
            Window.update();
        }

        running = false;
        try { logicThread.join(); } catch (InterruptedException e) { System.out.println(e.getMessage()); }

        org.lwjgl.vulkan.VK10.vkDeviceWaitIdle(VulkanContext.getDevice());

        ui.Container root = Window.getContentPane();
        if (root != null) {
            root.destroy();
        }

        MasterRenderer.destroy();
        MeshLoader.destroy();
        loader.GeomRegistry.destroy();
        TextureRegistry.destroy();

        environment.RendererManager.destroy();
        sharedState.destroy();

        VulkanContext.destroy();
        Window.destroy();
    }

    private static void runLogicLoop() {
        long lastTime = System.nanoTime();
        final double nsPerTick = 1000000000.0 / 60.0;
        double delta = 0;

        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;
            lastTime = now;

            boolean ticked = false;

            while (delta >= 1) {
                float deltaInSeconds = 1.0f / 60.0f;
                ui.Container root = Window.getContentPane();
                if (root != null) {
                    root.update(deltaInSeconds);
                    root.updateTransform(0f, 0f, false);
                }
                delta--;
                ticked = true;
            }

            if (ticked) {
                ui.Container root = Window.getContentPane(); // <--- Now uses Window
                if (root != null) {
                    RenderState backBuffer = sharedState.getBackBuffer();
                    backBuffer.clear();

                    root.extract3DEntities(backBuffer);
                    root.extractUIData(backBuffer);

                    sharedState.swap();
                }
            }
            Thread.yield();
        }
    }
}