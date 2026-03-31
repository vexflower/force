package engine;

import entity.Entity;
import hardware.Display;
import loader.MeshLoader;
import loader.TextureLoader;
import model.Mesh;
import renderer.MasterRenderer;
import ui.Panel;
import ui.scene.Scene3D;

public class Main {
    public static void main(String[] args) {
        Display.createDisplay(0, 1280, 720);
        Display.setShowFPSTitle(true);
        MasterRenderer.setRenderer(); // MANDATORY TO SET IT BEFORE INITIALIZING EVERYTHING. i dont need to set commandpool as the parameter.

        // 1. The 3D Scene Container
        Scene3D scene = new Scene3D(1280, 720);
        Display.setContentPane(scene);
        scene.init();

        // 2. The 3D Camera
        entity.Camera cam = new entity.Camera();
        cam.setPosition(0f, 20f, 50f); // Step back to look at the fox
        scene.setCamera(cam);

        // 2. High-Level Entity Creation
        Mesh fox = MeshLoader.loadObject("obj/fox.obj", true, false);
        int texture = TextureLoader.loadTexture("images/fox.jpg");
        Entity ent = new Entity(fox, texture);
        ent.setPosition(0, 20f, -200f);
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