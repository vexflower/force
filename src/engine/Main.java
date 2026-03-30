package engine;

import environment.WorldScene;
import hardware.Display;
import renderer.MasterRenderer;
import ui.Panel;
import ui.scene.Scene3D;

public class Main {
     static void main(String[] args) {
         System.out.println("--- Booting Force Engine ---");

         // 1. Ignite the Hardware
         Display.createDisplay(0, 1280, 720);
         Display.setShowFPSTitle(true);
         MasterRenderer.setRenderer();

         // 2. Set up the Initial Scene
         Scene3D initialScene = new Scene3D(1280, 720);
         initialScene.init(MasterRenderer.getCommandPool());

         // 3. Mount the UI Tree (Swing Paradigm)
         Panel rootPane = new Panel(1280, 720);
         rootPane.setBackgroundColor(0, 0, 0, 0); // Invisible stretching root pane
         rootPane.init();
         initialScene.setContentPane(rootPane);

         // Child panel locked 50 pixels from the edge of the Root Pane!
         Panel myPanel = new Panel(500, 500);
         myPanel.setBackgroundColor(0.8f, 0.1f, 0.1f, 0.7f);
         myPanel.init();
         myPanel.setPosition(50, 50);
         rootPane.add(myPanel); // Add to the Root Pane, not the Scene!

         // 4. Hand control to the GameEngine Loop
         GameEngine.start(initialScene);
    }
}