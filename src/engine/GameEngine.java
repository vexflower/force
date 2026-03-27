package engine; // Make sure this matches what HotswapManager is looking for!

import hardware.Display;
import hardware.Keyboard;
import hotswap.EngineState;
import lang.Mat4;
import renderer.MasterRenderer;

import static java.lang.Math.toRadians;

public class GameEngine {

    private boolean running = false;
    private boolean hotswapRequested = false;
    private EngineState currentState;

    public static void main(String... args)
    {

        System.out.println("Engine initialized from Bootloader.");

        // 1. Ignite the Hardware
        Display.createDisplay(1280, 720);
        Display.setShowFPSTitle(true);
        MasterRenderer.setRenderer();

        // 2. State Injection
        // If state.persistentData contains a "player_rotation", load it here!
        Mat4 mySpinningMatrix = new Mat4();
        float currentRotation = 0;

        // 3. The Core Loop
        while(!Display.shouldDisplayClose()) {

            // Listen for a secret key combination to trigger a hotswap (e.g., F5)
            if(Keyboard.isKeyDown(Keyboard.ZERO)) {
                System.out.println("F5 Pressed: Engine signaling Bootloader for Hotswap...");

            }

            mySpinningMatrix.identity();
            mySpinningMatrix.translate(0.0f, 0.0f, 0.5f);

            currentRotation += (float) toRadians(Display.getDeltaInSeconds() * 100f);
            mySpinningMatrix.rotateY(currentRotation);

            MasterRenderer.render(mySpinningMatrix);
            Display.updateDisplay();
        }

        // 4. Graceful Teardown (Screen goes black temporarily)
        MasterRenderer.destroy();
        Display.closeDisplay();
    }

}