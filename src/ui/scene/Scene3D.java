package ui.scene;

import hardware.Display;
import lang.Mat4;
import lang.GeomMath;

public class Scene3D extends Scene {

    private int myBratSquareId;
    private float currentRotation = 0f;

    private Mat4 projectionMatrix = new Mat4();
    private Mat4 viewMatrix = new Mat4();
    private Mat4 modelMatrix = new Mat4();
    private Mat4 finalMVP = new Mat4();

    public Scene3D(int width, int height) {
        super(width, height);
    }

    @Override
    public void init(long commandPool) {
        int bratTextureId = resources.Resources.loadTexture("images/brat.png", commandPool);
        myBratSquareId = addEntity();
        environment.RendererManager.meshIds.set(myBratSquareId, model.Mesh.SQUARE.vaoId);
        environment.RendererManager.diffuseTextureIds.set(myBratSquareId, bratTextureId);

        // Initial perspective calculation
        projectionMatrix.perspective(70f, (float) Display.getWidth() / Display.getHeight(), 0.1f, 1000f);
        viewMatrix.identity().translate(0, 0, -2.0f);
    }

    // [NEW] Catch the resize event and fix the 3D stretching!
    @Override
    protected void onResize(int width, int height) {
        if (height == 0) height = 1; // Prevent division by zero if window is minimized
        float aspect = (float) width / (float) height;

        // Rebuild the perspective matrix with the exact new screen proportions
        projectionMatrix.perspective(70f, aspect, 0.1f, 1000f);
    }

    @Override
    public void update(float delta) {
        currentRotation += GeomMath.toRadians(90f * delta);
        modelMatrix.identity().rotateY(currentRotation);

        finalMVP.identity().mul(modelMatrix).mul(viewMatrix).mul(projectionMatrix);
        finalMVP.storeIntoFloatList(environment.RendererManager.transforms, myBratSquareId * 16);
    }
}