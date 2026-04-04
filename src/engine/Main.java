package engine;

import entity.Entity;
import hardware.VulkanContext;
import hardware.Window;
import loader.MeshLoader;
import loader.TextureLoader;
import model.Mesh;
import renderer.MasterRenderer;
import ui.Panel;
import ui.scene.Scene3D;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        Window.create(1280, 720);
        Window.setShowFPSTitle(true); // <--- Moved to Window
        VulkanContext.init();
        MasterRenderer.setRenderer();

        Scene3D scene = new Scene3D(1280, 720);
        scene.setBackgroundColor(0, 0, 0, 0);
        scene.requiresOffscreen = false;
        Window.setContentPane(scene); // <--- Now uses Window

        Scene3D scene2 = new Scene3D(400, 400);
        scene2.setBackgroundColor(.2f, .2f, .9f, .4f);
        scene2.setPosition(100, 100);

        Entity brat = new Entity(Mesh.CUBE, TextureLoader.loadTexture("images/brat.png"));
        brat.setPosition(0, 0,-150);
        brat.setRotation(0, 200f, 0);
        brat.scale = 10f;
        scene2.addEntity(brat);
        scene.add(scene2);

        entity.Camera cam = new entity.Camera();
        cam.setPosition(0f, 20f, 50f);
        scene.setCamera(cam);
        scene2.setCamera(cam);
        scene2.updatesCamera = false;

        Mesh fox = MeshLoader.loadObject("obj/fox.obj", true, false);
        int texture = TextureLoader.loadTexture("images/fox.jpg");
        Entity ent = new Entity(fox, texture);
        ent.setPosition(0, 20f, -200f);
        ent.moveRotate(0f, 90f, 0f, -1f);
        scene.addEntity(ent);

        Random random = new Random();
        for(int i = 0; i < 500; i++) {
            Entity foxIter = new Entity(fox, texture);
            foxIter.setPosition(random.nextFloat(500) - 250, random.nextFloat(500) - 250, random.nextFloat(500) - 250);
            foxIter.moveRotate(random.nextFloat(10), random.nextFloat(300), random.nextFloat(20), -1f);
            scene.addEntity(foxIter);
        }

        brat.moveRotate(0f, -90f, 0,-1);

        Panel myPanel = new Panel(300, 300);
        myPanel.setBackgroundColor(0.8f, 0.1f, 0.1f, 0.7f);
        myPanel.setPosition(50, 50);
        scene.add(myPanel);

        GameEngine.start();
    }
}