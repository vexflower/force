package hotswap;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public final class HotswapManager {

    private IEngine currentEngine;
    private URLClassLoader currentClassLoader;

    public void bootFirstEngine(File engineJar) throws Exception {
        loadAndStartEngine(engineJar, new EngineState()); // Empty state for fresh boot
    }

    /**
     * Executes a Level 4 Engine Hotswap.
     * 1. Pauses current engine.
     * 2. Extracts game state (player position, loaded scene).
     * 3. Destroys GLFW/LWJGL (Screen goes black).
     * 4. Loads new Engine from GitHub.
     * 5. Injects state into new Engine and resumes.
     */
    public void performLevel4Hotswap(File newEngineJar) {
        System.out.println("Starting Engine Swap...");

        // 1. Extract state (Where is the player? What level is loaded?)
        EngineState savedState = currentEngine.stopAndExtractState();

        // 2. The old engine has gracefully called Display.closeDisplay() and glfwTerminate().
        // The screen is now black. We drop the reference to the old engine.
        currentEngine = null;

        try {
            // 3. Close the old class loader to free the old classes from RAM
            if (currentClassLoader != null) {
                currentClassLoader.close();
            }

            // Optional: Thread.sleep(2000) here if you want a dramatic pause,
            // or let the file downloading process naturally take a few seconds.

            // 4. Load the new engine
            loadAndStartEngine(newEngineJar, savedState);

        } catch (Exception e) {
            System.err.println("FATAL ERROR during hotswap. Engine crashed.");
            // Fallback: Boot the old JAR again so the game doesn't die completely
        }
    }

    private void loadAndStartEngine(File jarFile, EngineState state) throws Exception {
        // Create a completely new memory space for the new classes
        currentClassLoader = new URLClassLoader(
                new URL[]{jarFile.toURI().toURL()},
                this.getClass().getClassLoader() // Parent loader (holds IEngine interface)
        );

        // Use Reflection to instantiate the "Main" class of the new engine
        Class<?> engineClass = currentClassLoader.loadClass("framework.core.GameEngine");

        currentEngine = (IEngine) engineClass.getDeclaredConstructor().newInstance();

        // Boot the new engine, passing the old state!
        // The new engine will call Display.createDisplay() and the screen turns back on.
        currentEngine.start(state);
    }
}