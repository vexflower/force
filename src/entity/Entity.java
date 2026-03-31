package entity;

import environment.RendererManager;
import lang.GeomMath;
import lang.Mat4;
import model.Mesh;

public class Entity {
    public final int id;

    // Public state for easy access
    public float x, y, z;
    public float rotX, rotY, rotZ;
    public float scale = 1.0f;

    // Animation state
    private float velRotX, velRotY, velRotZ;
    private float rotDuration;

    // Private zero-GC math objects
    private final Mat4 modelMatrix = new Mat4();
    private static final Mat4 SCRATCH_MVP = new Mat4();

    public Entity(Mesh mesh, int textureId) {
        this.id = RendererManager.createEntity();
        RendererManager.meshIds.set(id, mesh.vaoId);
        RendererManager.diffuseTextureIds.set(id, textureId);
    }

    public void setPosition(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
    }

    public void setRotation(float rx, float ry, float rz) {
        this.rotX = rx; this.rotY = ry; this.rotZ = rz;
    }

    public void moveRotate(float rx, float ry, float rz, float duration) {
        this.velRotX = rx; this.velRotY = ry; this.velRotZ = rz;
        this.rotDuration = duration;
    }

    /**
     * Ticks logic and pushes the final matrix directly to the global FloatList.
     * @param vpMatrix The View-Projection matrix from the Scene.
     */
    public void update(float delta, Mat4 vpMatrix) {
        // Handle infinite (-1) or finite rotation
        if (rotDuration != 0) {
            rotX += velRotX * delta;
            rotY += velRotY * delta;
            rotZ += velRotZ * delta;
            if (rotDuration > 0) {
                rotDuration -= delta;
                if (rotDuration <= 0) rotDuration = 0;
            }
        }

        // 1. Unrolled TRS calculation directly into modelMatrix
        GeomMath.createTransformationMatrix(x, y, z, rotX, rotY, rotZ, scale, scale, scale, modelMatrix);

        // 2. Multiply Model * (View * Projection) without allocating new memory
        vpMatrix.mul(modelMatrix, SCRATCH_MVP);

        // 3. Write straight into the flat data array for the GPU!
        SCRATCH_MVP.storeIntoFloatList(RendererManager.transforms, id * 16);
    }
}