package environment;

import lang.Mat4;

public class WorldScene extends Scene {

    private int myBratSquareId;
    private float currentRotation = 0f;

    // The FBO/Camera Logic Matrices
    private Mat4 projectionMatrix = new Mat4();
    private Mat4 viewMatrix = new Mat4();
    private Mat4 modelMatrix = new Mat4();
    private Mat4 finalMVP = new Mat4();

    @Override
    public void init(long commandPool) {
        int bratTextureId = resources.Resources.loadTexture("images/brat.png", commandPool);
        myBratSquareId = addEntity();
        environment.RendererManager.meshIds.set(myBratSquareId, model.Mesh.SQUARE.vaoId);
        environment.RendererManager.diffuseTextureIds.set(myBratSquareId, bratTextureId);

        // [CHANGED: Set up the Camera Lens]
        // 70 degree FOV, 16:9 Aspect Ratio
        projectionMatrix.perspective(70f, 1280f / 720f, 0.1f, 1000f);

        // Pull the camera BACK 2 units so we can see the square
        viewMatrix.identity();
        viewMatrix.translate(0, 0, -2.0f);
    }

    @Override
    public void update(float delta) {
        currentRotation += lang.GeomMath.toRadians(90f * delta);

        // 1. Build the Entity's Local Transform
        modelMatrix.identity();
        modelMatrix.rotateY(currentRotation);

        // 2. Calculate Final MVP (Order matters! Projection * View * Model)
        finalMVP.identity();
        finalMVP.mul(projectionMatrix);
        finalMVP.mul(viewMatrix);
        finalMVP.mul(modelMatrix);

        // 3. Dump the perfectly calculated MVP into the renderer
        int offset = myBratSquareId * 16;
        finalMVP.storeIntoFloatList(environment.RendererManager.transforms, offset);
    }
}