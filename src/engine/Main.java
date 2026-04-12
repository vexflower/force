package engine;

import entity.Entity;
import entity.Camera;
import hardware.VulkanContext;
import hardware.Window;
import loader.MeshLoader;
import loader.MeshRegistry;
import loader.TextureLoader;
import mesh.Mesh;
import move.Move;
import move.MoveType;
import renderer.MasterRenderer;
import ui.Panel;
import ui.scene.Scene3D;

public class Main {
    public static void main(String[] args) {
        Window.create(1280, 720);
        Window.setShowFPSTitle(true);
        VulkanContext.init();
        MasterRenderer.setRenderer();

        // 1. --- LOAD ALL GEOMETRY INTO CPU RAM ---
        Mesh.initPrimitives();
        MeshLoader.loadObject("obj/fox.obj", true, false);

        // 2. --- UPLOAD THE MEGA-BUFFERS TO THE GPU ---
        loader.GeomRegistry.uploadToGPU();

        // [NEW] 2.5 --- LINK THE SSBOs TO THE SHADER DESCRIPTOR SET ---
        shader.VKShader.bindSSBOs(renderer.MasterRenderer.getGlobalEntityBuffer(), loader.GeomRegistry.gpuVertexBuffer);

        // 3. --- LOAD TEXTURES ---
        int foxTex = TextureLoader.loadTexture("images/fox.jpg");
        int bratTex = TextureLoader.loadTexture("images/brat.png");

        // 4. --- BUILD THE SCENE ---
        Scene3D scene = new Scene3D(1280, 720);
        scene.setBackgroundColor(0, 0, 0, 1);
        scene.requiresOffscreen = false;
        Window.setContentPane(scene);

        // --- THE FLOOR ---
        // We use the Square mesh, scale it up to 200, and rotate it flat
        Entity floor = new Entity(MeshRegistry.get("floor"), bratTex); // Reusing fox tex for now
        floor.setPosition(0, 0, 0); // Put it slightly below the camera
        floor.setRotation(0, 0, 0); // Lay it flat on the ground
        floor.scale = 2000f;
        scene.addEntity(floor);

        Scene3D scene2 = new Scene3D(400, 400);
        scene2.setBackgroundColor(.2f, .2f, .9f, .4f);
        scene2.setPosition(100, 100);

        // Access meshes intuitively by String!
        Entity brat = new Entity(MeshRegistry.get("cube"), bratTex);
        brat.setPosition(0, 0, 150);
        brat.setRotation(0, 200f, 0);
        brat.scale = 50f;
        scene2.addEntity(brat);
        scene2.addEntity(floor);
        scene.add(scene2);

        Camera cam = new Camera();
        cam.setPosition(0f, 20f, 0);
        scene.setCamera(cam);
        scene2.setCamera(cam);
        scene2.updatesCamera = false;

        // Inside Main.java
        // Replace the old ent setup with this:
        Entity ent = new Entity(MeshRegistry.get("fox"), foxTex);
        ent.setPosition(0, 200f, 200f); // Start way up in the air
        ent.scale = 50f;
        scene.addEntity(ent);

        // Tell the engine to drop it to Y=20 over 2 seconds using a bouncy curve
        ent.move(Move.BOUNCE_OUT, MoveType.POINT, 0f, 0f, 200f, 3f);

        scene.addEntity(ent);

        java.util.Random random = new java.util.Random();
//        for(int i = 0; i < 1000; i++) {
//            Entity foxIter = new Entity(MeshRegistry.get("fox"), foxTex);
//            foxIter.scale = 30f;
//            foxIter.setPosition(random.nextFloat(500) - 250, random.nextFloat(500) - 250, random.nextFloat(500) - 250);
//            //oxIter.moveRotate(random.nextFloat(10), random.nextFloat(300), random.nextFloat(20), -1f);
//            scene.addEntity(foxIter);
//        }

        brat.moveRotate(0f, -90f, 0, -1);

        Panel myPanel = new Panel(300, 300);
        myPanel.setBackgroundColor(0.8f, 0.1f, 0.1f, 0.7f);
        myPanel.setPosition(50, 50);
        scene.add(myPanel);

        // 5. --- START THE ENGINE ---
        GameEngine.start();
    }
}