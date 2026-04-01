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

    // [NEW] Toggle this to lock the firewall for stacked scenes!
    public boolean isViewportPanel = false;

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
    public void extract3DEntities(renderer.RenderState state) {
        if (!this.isViewportPanel) {
            // I am the Main Window. I will send my 3D entities (The Fox) to the global pass.
            super.extract3DEntities(state);
        }
        // when else:
        // [THE FIREWALL]: I am a sealed JPanel! I will NOT leak my 3D entities to the global pass!
        // Do absolutely nothing here. The FBO pass will handle it natively.
    }

    @Override
    public void extractUIData(renderer.RenderState state) {
        // [THE FIREWALL]: Guarantee FBO allocation before the UI tries to draw it!
        if (this.textureId == -1 && this.isViewportPanel && this.width > 0 && this.height > 0) {
            this.init();
        }
        super.extractUIData(state);
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