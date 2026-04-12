package entity;

import hardware.Keyboard;
import hardware.Mouse;
import lang.Mat4;
import lang.GeomMath;
import move.Move;

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
    // --- ZERO-GC PRE-ALLOCATED COMPONENTS ---
    public final Move posMove = new Move();
    public final Move rotMove = new Move();

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
        boolean matrixNeedsUpdate = false;

        // Process Automated Position
        if (posMove.active) {
            if (posMove.duration == -1f) {
                posX += posMove.endX * deltaTime;
                posY += posMove.endY * deltaTime;
                posZ += posMove.endZ * deltaTime;
                matrixNeedsUpdate = true;
            } else {
                posMove.time += deltaTime;
                float t = Math.min(posMove.time / posMove.duration, 1.0f);
                float eased = posMove.getEased(t);
                this.posX = posMove.startX + (posMove.endX - posMove.startX) * eased;
                this.posY = posMove.startY + (posMove.endY - posMove.startY) * eased;
                this.posZ = posMove.startZ + (posMove.endZ - posMove.startZ) * eased;
                matrixNeedsUpdate = true;
                if (t >= 1.0f) posMove.active = false;
            }
        }

        // Process Automated Rotation
        if (rotMove.active) {
            if (rotMove.duration == -1f) {
                rotX += rotMove.endX * deltaTime;
                rotY += rotMove.endY * deltaTime;
                rotZ += rotMove.endZ * deltaTime;
                matrixNeedsUpdate = true;
            } else {
                rotMove.time += deltaTime;
                float t = Math.min(rotMove.time / rotMove.duration, 1.0f);
                float eased = rotMove.getEased(t);
                this.rotX = rotMove.startX + (rotMove.endX - rotMove.startX) * eased;
                this.rotY = rotMove.startY + (rotMove.endY - rotMove.startY) * eased;
                this.rotZ = rotMove.startZ + (rotMove.endZ - rotMove.startZ) * eased;
                matrixNeedsUpdate = true;
                if (t >= 1.0f) rotMove.active = false;
            }
        }

        // Process Manual Input
        if (isCameraMoveable && handleManualInput(deltaTime)) {
            matrixNeedsUpdate = true;
        }

        if (matrixNeedsUpdate) {
            updateViewMatrix();
        }
    }

    // Extracted the big keyboard block into a cleaner helper method
    private boolean handleManualInput(float distance) {
        distance *= WALK_SPEED;
        boolean moved = false;

        float mouseX = Mouse.getX();
        float mouseY = Mouse.getY();

        if (Mouse.isButtonDown(Mouse.RIGHT)) {
            if (firstMouse) { lastMouseX = mouseX; lastMouseY = mouseY; firstMouse = false; }
            float dx = mouseX - lastMouseX;
            float dy = mouseY - lastMouseY;
            rotY += dx * MOUSE_SENSITIVITY;
            rotX += dy * MOUSE_SENSITIVITY;
            if (rotX > 89.0f) rotX = 89.0f;
            if (rotX < -89.0f) rotX = -89.0f;
            moved = true;
        } else {
            firstMouse = true;
        }
        lastMouseX = mouseX; lastMouseY = mouseY;

        float yawRad = GeomMath.toRadians(rotY);
        float forwardX = lang.FastMath.sin32(yawRad);
        float forwardZ = lang.FastMath.cos32(yawRad);
        float rightX = lang.FastMath.cos32(yawRad);
        float rightZ = -lang.FastMath.sin32(yawRad);

        if (Keyboard.isKeyDown(Keyboard.W)) { posX += forwardX * distance; posZ += forwardZ * distance; moved = true; }
        if (Keyboard.isKeyDown(Keyboard.S)) { posX -= forwardX * distance; posZ -= forwardZ * distance; moved = true; }
        if (Keyboard.isKeyDown(Keyboard.D)) { posX += rightX * distance; posZ += rightZ * distance; moved = true; }
        if (Keyboard.isKeyDown(Keyboard.A)) { posX -= rightX * distance; posZ -= rightZ * distance; moved = true; }
        if (Keyboard.isKeyDown(Keyboard.SPACE)) { posY += distance; moved = true; }
        if (Keyboard.isKeyDown(Keyboard.LEFT_SHIFT)) { posY -= distance; moved = true; }

        return moved;
    }

    public void updateViewMatrix() {
        GeomMath.createViewMatrix(posX, posY, posZ, rotX, rotY, rotZ, viewMatrix);
    }

    public void setMoveable(boolean flag)
    {
        isCameraMoveable = flag;
    }
}