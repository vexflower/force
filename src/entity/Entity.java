package entity;

import environment.RendererManager;
import lang.GeomMath;
import lang.Mat4;
import model.Mesh;

public class Entity {
    public final int id;

    public float x, y, z;
    public float rotX, rotY, rotZ;
    public float scale = 1.0f;

    private float velRotX, velRotY, velRotZ;
    private float rotDuration;

    private final Mat4 modelMatrix = new Mat4();
    public final Mat4 mvpMatrix = new Mat4(); // <--- NEW: Stores final matrix here

    public Entity(Mesh mesh, int textureId) {
        this.id = RendererManager.createEntity();
        RendererManager.meshIds.set(id, mesh.vaoId);
        RendererManager.diffuseTextureIds.set(id, textureId);
    }

    public void setPosition(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    public void setRotation(float rx, float ry, float rz) { this.rotX = rx; this.rotY = ry; this.rotZ = rz; }

    public void moveRotate(float rx, float ry, float rz, float duration) {
        this.velRotX = rx; this.velRotY = ry; this.velRotZ = rz;
        this.rotDuration = duration;
    }

    public void update(float delta, Mat4 vpMatrix) {
        if (rotDuration != 0) {
            rotX += velRotX * delta; rotY += velRotY * delta; rotZ += velRotZ * delta;
            if (rotDuration > 0) { rotDuration -= delta; if (rotDuration <= 0) rotDuration = 0; }
        }

        GeomMath.createTransformationMatrix(x, y, z, rotX, rotY, rotZ, scale, scale, scale, modelMatrix);

        // Multiply and store securely in the Entity
        vpMatrix.mul(modelMatrix, mvpMatrix);
    }
}