package entity;

import hardware.Keyboard;
import hardware.Mouse;
import lang.Mat4;
import lang.GeomMath;

public class Camera {
    public float posX, posY, posZ;
    public float rotX, rotY, rotZ; // x = pitch, y = yaw, z = roll

    public final Mat4 viewMatrix = new Mat4();
    private static final float WALK_SPEED = 50f;
    private static final float MOUSE_SENSITIVITY = 0.15f;

    // Mouse tracking state
    private float lastMouseX = -1;
    private float lastMouseY = -1;
    private boolean firstMouse = true;
    private boolean isCameraMoveable = true;

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

    public void move(float deltaTime) {
        if(!isCameraMoveable) return;

        float distance = WALK_SPEED * deltaTime;
        boolean moved = false;

        float mouseX = Mouse.getX();
        float mouseY = Mouse.getY();

        if (Mouse.isButtonDown(Mouse.RIGHT)) {
            if (firstMouse) {
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                firstMouse = false;
            }

            float dx = mouseX - lastMouseX;
            float dy = mouseY - lastMouseY;

            // [FIXED] Standard FPS Mouse Mapping
            rotY += dx * MOUSE_SENSITIVITY;
            rotX += dy * MOUSE_SENSITIVITY;

            if (rotX > 89.0f) rotX = 89.0f;
            if (rotX < -89.0f) rotX = -89.0f;

            moved = true;
        } else {
            firstMouse = true;
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // --- DIRECTIONAL KEYBOARD MOVEMENT ---
        float yawRad = GeomMath.toRadians(rotY);
        // [FIXED] Left-Handed Vector Math (+Z Forward, +X Right)
        float forwardX = lang.FastMath.sin32(yawRad);
        float forwardZ = lang.FastMath.cos32(yawRad);

        float rightX = lang.FastMath.cos32(yawRad);
        float rightZ = -lang.FastMath.sin32(yawRad);

        if (Keyboard.isKeyDown(Keyboard.W)) {
            posX += forwardX * distance;
            posZ += forwardZ * distance;
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.S)) {
            posX -= forwardX * distance;
            posZ -= forwardZ * distance;
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.D)) {
            posX += rightX * distance;
            posZ += rightZ * distance;
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.A)) {
            posX -= rightX * distance;
            posZ -= rightZ * distance;
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.SPACE)) {
            posY += distance; // Fly Up (+Y)
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.LEFT_SHIFT)) {
            posY -= distance; // Fly Down (-Y)
            moved = true;
        }

        if (moved) {
            updateViewMatrix();
        }

        // debug when needed
        // System.out.printf("Camera Position: %f, %f, %f", posX, posY, posZ);
    }

    public void updateViewMatrix() {
        GeomMath.createViewMatrix(posX, posY, posZ, rotX, rotY, rotZ, viewMatrix);
    }

    public void setMoveable(boolean flag)
    {
        isCameraMoveable = flag;
    }
}