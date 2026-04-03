package ui.scene;

import hardware.VulkanContext;
import hardware.Window;
import lang.Mat4;
import entity.Camera;

public class Scene3D extends Scene {

    protected Mat4 projectionMatrix = new Mat4();
    private final Mat4 vpMatrix = new Mat4();
    private float fov = 70f;
    private float zNear = .1f;
    private float zFar = 10_000f;
    public boolean updatesCamera = true;
    private Camera currentCamera;

    public Scene3D(int width, int height) {
        super(width, height);
        currentCamera = new Camera();
        currentCamera.setPosition(0, 0, 20f);
        onResize(width, height); // Force matrix instantly
    }

    public void setCamera(Camera camera) { this.currentCamera = camera; }


    @Override
    protected void onResize(int width, int height) {
        if (height == 0) height = 1;
        float aspect = (float) width / (float) height;
        projectionMatrix.perspective(this.fov, aspect, this.zNear, this.zFar);
    }

    @Override
    public void update(float delta) {
        if (currentCamera != null) {
            if (this.updatesCamera) currentCamera.move(delta);
            // In Scene3D.java update()
            projectionMatrix.mul(currentCamera.viewMatrix, vpMatrix);
            for (int i = 0; i < activeEntities.size(); i++) {
                activeEntities.get(i).update(delta, vpMatrix);
            }
        }
        super.update(delta);
    }
}