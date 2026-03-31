package engine;

import entity.Entity;
import hardware.Display;
import loader.TextureLoader;
import model.Mesh;
import renderer.MasterRenderer;
import ui.Panel;
import ui.scene.Scene3D;

public class Main {
    public static void main(String[] args) {
        Display.createDisplay(0, 1280, 720);
        Display.setShowFPSTitle(true);
        MasterRenderer.setRenderer();

        // 1. The 3D Scene Container
        Scene3D scene = new Scene3D(1280, 720);
        Display.setContentPane(scene);
        scene.init(); // Init the camera

        // 2. High-Level Entity Creation
        int texture = TextureLoader.loadTexture("images/brat.png", MasterRenderer.getCommandPool());
        Entity ent = new Entity(Mesh.CUBE, texture);
        ent.setPosition(0, 0, -2f);
        ent.moveRotate(0f, 90f, 0f, -1f); // 90 degrees per second, infinite
        scene.addEntity(ent);

        // 3. Auto-initializing UI Panel
        Panel myPanel = new Panel(300, 300);
        myPanel.setBackgroundColor(0.8f, 0.1f, 0.1f, 0.7f);
        myPanel.setPosition(50, 50);
        scene.add(myPanel);

        // Run the engine
        GameEngine.start();
    }
}