package ui.scene;

import lang.Mat4;
import entity.Camera;

public class Scene3D extends Scene {

    protected Mat4 projectionMatrix = new Mat4();
    private final Mat4 vpMatrix = new Mat4();
    private float fov = 70f;
    private float zNear = .1f;
    private float zFar = 10_000f;
    // [NEW]: Determines if this scene is allowed to move the camera
    public boolean updatesCamera = true;

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
        // [THE FIX]: Use your new variables instead of hardcoded 70f, 0.1f, 1000f
        projectionMatrix.perspective(this.fov, aspect, this.zNear, this.zFar);
    }

    @Override
    public void update(float delta) {
        if (currentCamera != null) {
            /// [THE FIX]: Only move the camera if this scene has permission!
            if (this.updatesCamera) {
                currentCamera.move(delta);
            }

            // 2. Combine Camera View and Scene Projection once per frame
            projectionMatrix.mul(currentCamera.viewMatrix, vpMatrix);

            // 3. Tick all entities and pass the combined View-Projection matrix
            for (int i = 0; i < activeEntities.size(); i++) {
                activeEntities.get(i).update(delta, vpMatrix);
            }
        }

        // [THE FIX]: Pass the engine tick down to all children (like scene2)!
        super.update(delta);
    }
}