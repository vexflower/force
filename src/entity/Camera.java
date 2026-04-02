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

        if(!isCameraMoveable)
            return;

        float distance = WALK_SPEED * deltaTime;
        boolean moved = false;

        // --- 1. MOUSE LOOK (Hold Right-Click to pan around) ---
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

            // Apply sensitivity
            rotY += dx * MOUSE_SENSITIVITY; // Yaw (Looking left/right)
            rotX -= dy * MOUSE_SENSITIVITY; // Pitch (Looking up/down)

            // Clamp pitch to prevent the camera from doing a backflip
            if (rotX > 89.0f) rotX = 89.0f;
            if (rotX < -89.0f) rotX = -89.0f;

            moved = true;
        } else {
            // Reset state when letting go of right-click so it doesn't snap
            firstMouse = true;
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // --- 2. DIRECTIONAL KEYBOARD MOVEMENT ---
        // Calculate the direction we are facing using FastMath
        float yawRad = GeomMath.toRadians(rotY);
        float sinYaw = lang.FastMath.sin32(yawRad);
        float cosYaw = lang.FastMath.cos32(yawRad);

        if (Keyboard.isKeyDown(Keyboard.W)) {
            posX += sinYaw * distance;
            posZ -= cosYaw * distance;
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.S)) {
            posX -= sinYaw * distance;
            posZ += cosYaw * distance;
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.A)) {
            posX -= cosYaw * distance;
            posZ -= sinYaw * distance;
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.D)) {
            posX += cosYaw * distance;
            posZ += sinYaw * distance;
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.SPACE)) {
            posY -= distance; // Fly Up
            moved = true;
        }
        if (Keyboard.isKeyDown(Keyboard.LEFT_SHIFT)) {
            posY += distance; // Fly Down
            moved = true;
        }

        if (moved) {
            updateViewMatrix();
        }
    }

    public void updateViewMatrix() {
        GeomMath.createViewMatrix(posX, posY, posZ, rotX, rotY, rotZ, viewMatrix);
    }

    public void setMoveable(boolean flag)
    {
        isCameraMoveable = flag;
    }
}