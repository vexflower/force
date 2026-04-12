package ui.scene;

import hardware.VulkanContext;
import hardware.Window;
import lang.Mat4;
import entity.Camera;
import move.Move;
import move.MoveType;

public class Scene3D extends Scene {

    protected Mat4 projectionMatrix = new Mat4();
    private final Mat4 vpMatrix = new Mat4();
    private float fov = 70f;
    private float zNear = .1f;
    private float zFar = 10_000f;
    public boolean updatesCamera = true;
    Camera currentCamera;

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
                activeEntities.get(i).update(delta);
            }
        }
        super.update(delta);
    }

    // ---> THE FIX: Inject the Camera Matrix into the Snapshot! <---
    @Override
    public void extract3DEntities(renderer.RenderState state) {
        if (state.snapshotCount < state.snapshots.length) {
            // Push Matrix natively to C-Memory!
            vpMatrix.store(state.snapshots[state.snapshotCount].vpMatrix);
        }
        super.extract3DEntities(state);
    }

    // --- SCENE & CAMERA ANIMATION API ---

    public void moveScene(int moveCurve, int moveType, float destX, float destY, float duration) {
        int curve = duration == -1f ? Move.NORMAL : moveCurve;
        float ex = moveType == MoveType.STUDS ? localX + destX : destX;
        float ey = moveType == MoveType.STUDS ? localY + destY : destY;

        // If infinite, we just pass the velocities in directly as the end targets
        if (duration == -1f) { ex = destX; ey = destY; }

        posMove.start(curve, localX, localY, 0, ex, ey, 0, duration);
    }

    public void moveCamera(int moveCurve, int moveType, float destX, float destY, float destZ, float duration) {
        if (currentCamera == null) return;
        int curve = duration == -1f ? Move.NORMAL : moveCurve;
        float ex = moveType == MoveType.STUDS ? currentCamera.posX + destX : destX;
        float ey = moveType == MoveType.STUDS ? currentCamera.posY + destY : destY;
        float ez = moveType == MoveType.STUDS ? currentCamera.posZ + destZ : destZ;

        if (duration == -1f) { ex = destX; ey = destY; ez = destZ; }

        currentCamera.posMove.start(curve, currentCamera.posX, currentCamera.posY, currentCamera.posZ, ex, ey, ez, duration);
    }

    public void rotateCamera(int moveCurve, int moveType, float pitch, float yaw, float roll, float duration) {
        if (currentCamera == null) return;
        int curve = duration == -1f ? Move.NORMAL : moveCurve;
        float ex = moveType == MoveType.STUDS ? currentCamera.rotX + pitch : pitch;
        float ey = moveType == MoveType.STUDS ? currentCamera.rotY + yaw : yaw;
        float ez = moveType == MoveType.STUDS ? currentCamera.rotZ + roll : roll;

        if (duration == -1f) { ex = pitch; ey = yaw; ez = roll; }

        currentCamera.rotMove.start(curve, currentCamera.rotX, currentCamera.rotY, currentCamera.rotZ, ex, ey, ez, duration);
    }
}