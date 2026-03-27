package entity;

import hardware.Keyboard;
import lang.Mat4;
import lang.GeomMath;

public class Camera {
    public float posX, posY, posZ;
    public float rotX, rotY, rotZ; // x = pitch, y = yaw, z = roll

    public final Mat4 viewMatrix = new Mat4();
    private static final float WALK_SPEED = 50f;

    public Camera() {
        updateViewMatrix();
    }

    public void setPosition(float x, float y, float z) {
        posX = x; posY = y; posZ = z;
        updateViewMatrix();
    }

    public void setRotation(float pitch, float yaw, float roll) {
        rotX = pitch; rotY = yaw; rotZ = roll;
        updateViewMatrix();
    }

    /**
     * Thread-Safe by design: this is only called by the Game Logic loop.
     * No synchronization locks blocking the GPU thread!
     */
    public void move(float deltaTime) {
        float distance = WALK_SPEED * deltaTime;
        boolean keyWasPressed = false;

        if (Keyboard.isKeyDown(Keyboard.W)) { posZ -= distance; keyWasPressed = true; }
        if (Keyboard.isKeyDown(Keyboard.S)) { posZ += distance; keyWasPressed = true; }
        if (Keyboard.isKeyDown(Keyboard.A)) { posX -= distance; keyWasPressed = true; }
        if (Keyboard.isKeyDown(Keyboard.D)) { posX += distance; keyWasPressed = true; }

        if (keyWasPressed) {
            updateViewMatrix();
        }
    }

    public void updateViewMatrix() {
        // Uses our unrolled algebraic view matrix generator!
        GeomMath.createViewMatrix(posX, posY, posZ, rotX, rotY, rotZ, viewMatrix);
    }
}