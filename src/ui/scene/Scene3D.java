package ui.scene;

import lang.Mat4;
import entity.Camera;

public class Scene3D extends Scene {

    protected Mat4 projectionMatrix = new Mat4();
    private final Mat4 vpMatrix = new Mat4();

    // [NEW] The dedicated camera for this 3D view
    private Camera currentCamera;

    public Scene3D(int width, int height) {
        super(width, height);
    }

    // [NEW] Attach the camera to the scene
    public void setCamera(Camera camera) {
        this.currentCamera = camera;
    }

    @Override
    public void init() {
        onResize(this.width, this.height);
    }

    @Override
    protected void onResize(int width, int height) {
        if (height == 0) height = 1;
        float aspect = (float) width / (float) height;
        projectionMatrix.perspective(70f, aspect, 0.1f, 1000f);
    }

    @Override
    public void update(float delta) {
        if (currentCamera != null) {
            // 1. Tick the Camera's input logic
            currentCamera.move(delta);

            // 2. Combine Camera View and Scene Projection once per frame
            projectionMatrix.mul(currentCamera.viewMatrix, vpMatrix);

            // 3. Tick all entities and pass the combined View-Projection matrix
            for (int i = 0; i < activeEntities.size(); i++) {
                activeEntities.get(i).update(delta, vpMatrix);
            }
        }
    }
}