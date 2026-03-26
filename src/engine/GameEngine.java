package engine;

import hardware.Display;
import hotswap.EngineState;
import hotswap.IEngine;

/**
 * The actual core of your video game. 
 * This is the file that gets downloaded and hot-swapped!
 */
public class GameEngine implements IEngine
{

    private boolean isRunning = false;
    private float playerX = 0f; // Example state

    @Override
    public void start(EngineState state) {
        System.out.println("Starting Game Engine Payload...");

        // 1. Unpack the briefcase from the previous engine version
        if (state.persistentData.containsKey("playerX")) {
            this.playerX = (float) state.persistentData.get("playerX");
            System.out.println("Restored player position to: " + playerX);
        }

        // 2. Boot up the Hardware (LWJGL window opens)
        Display.createDisplay(1280, 720);
        isRunning = true;

        // 3. Start the main game loop
        runGameLoop();
    }

    private void runGameLoop() {
        while (isRunning && !Display.shouldDisplayClose()) {
            // ... Update logic, poll keyboard, render ...

            // Simulating player moving right
            playerX += 0.01f;

            Display.updateDisplay();
        }
    }

    @Override
    public EngineState stopAndExtractState() {
        System.out.println("Initiating Shutdown for Hotswap...");
        isRunning = false;

        // 1. Pack the briefcase!
        EngineState stateToSave = new EngineState();
        stateToSave.persistentData.put("playerX", this.playerX);
        // stateToSave.persistentData.put("currentLevel", "Level_2");

        // 2. Kill the window (Screen goes black)
        Display.closeDisplay();

        // 3. Return the briefcase to the Bootloader so it survives the swap
        return stateToSave;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }
}