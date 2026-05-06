package engine;

import entity.Entity;
import entity.Camera;
import hardware.VulkanContext;
import hardware.Window;
import mesh.MeshLoader;
import loader.MeshRegistry;
import loader.TextureLoader;
import mesh.Mesh;
import move.Move;
import move.MoveType;
import renderer.MasterRenderer;
import shader.SpirVCompiler;
import ui.Panel;
import ui.scene.Scene3D;

public class Main {
    public static void main(String[] args) {
        Window.create(1280, 720);
        Window.setShowFPSTitle(true);
        VulkanContext.init();
        MasterRenderer.setRenderer();

        SpirVCompiler.main(null);

        // 1. --- LOAD ALL GEOMETRY INTO CPU RAM ---
        Mesh.initPrimitives();
        // Load the fox into the staging buffers BEFORE we upload to the GPU!
        MeshLoader.loadMeshlet("\\meshes\\fox.meshl");

        // 2. --- UPLOAD THE MEGA-BUFFERS TO THE GPU ---
        loader.GeomRegistry.uploadToGPU();

        // 2.5 --- LINK THE VAULTS TO THE COMPUTE SHADER ---
        renderer.MasterRenderer.linkVaults();

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
        floor.setScale(2000f);
        scene.addEntity(floor);

        Scene3D scene2 = new Scene3D(400, 400);
        scene2.setBackgroundColor(.2f, .2f, .9f, .4f);
        scene2.setPosition(100, 100);

        // ==========================================
        // --- THE WALLS (The Occluders) ---
        // ==========================================
        Mesh cubeMesh = MeshRegistry.get("cube");

        Entity northWall = new Entity(cubeMesh, bratTex);
        northWall.setPosition(0, 50f, 400f);
        northWall.setScale(800f, 100f, 20f); // 800 wide, 100 tall, 20 thick
        scene.addEntity(northWall);

        Entity southWall = new Entity(cubeMesh, bratTex);
        southWall.setPosition(0, 50f, -400f);
        southWall.setScale(800f, 100f, 20f);
        scene.addEntity(southWall);

        Entity eastWall = new Entity(cubeMesh, bratTex);
        eastWall.setPosition(400f, 50f, 0);
        eastWall.setScale(20f, 100f, 800f);
        scene.addEntity(eastWall);

        Entity westWall = new Entity(cubeMesh, bratTex);
        westWall.setPosition(-400f, 50f, 0);
        westWall.setScale(20f, 100f, 800f);
        scene.addEntity(westWall);

        // ==========================================
        // --- THE STRESS TEST (The Foxes) ---
        // ==========================================
        java.util.Random random = new java.util.Random();
        for(int i = 0; i < 1500; i++) {
            Entity foxIter = new Entity(MeshRegistry.get("fox"), foxTex);
            foxIter.setScale(20f);

            // Scatter them wildly between -1000 and +1000
            // This guarantees hundreds of them are completely hidden behind the walls!
            float rx = random.nextFloat(2000) - 1000;
            float rz = random.nextFloat(2000) - 1000;
            foxIter.setPosition(rx, 20f, rz);

            // Give them random rotations
            //foxIter.setRotation(0, random.nextFloat(360), 0);
            scene.addEntity(foxIter);
        }

        // ==========================================
        // --- SCENE SETUP & CAMERA ---
        // ==========================================
        Camera cam = new Camera();
        cam.setPosition(0f, 20f, 0); // Spawn exactly in the middle of the arena
        scene.setCamera(cam);

        Panel myPanel = new Panel(300, 300);
        myPanel.setBackgroundColor(0.8f, 0.1f, 0.1f, 0.7f);
        myPanel.setPosition(50, 50);
        scene.add(myPanel);

        floor.isOccluder = 1.0f;
        northWall.isOccluder = 1.0f;
        southWall.isOccluder = 1.0f;
        eastWall.isOccluder = 1.0f;
        westWall.isOccluder = 1.0f;

        // 5. --- START THE ENGINE ---
        GameEngine.start();
    }
}