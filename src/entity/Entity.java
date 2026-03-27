package entity;

import lang.Mat4;
import lang.GeomMath;
import model.TexturedModel;

public class Entity {

    private TexturedModel model;
    public float posX, posY, posZ;
    public float rotX, rotY, rotZ;
    public float scaleX = 1f, scaleY = 1f, scaleZ = 1f;

    private float red = 1f, green = 1f, blue = 1f, alpha = 1f;

    // The pre-allocated matrix. Zero GC!
    public final Mat4 transformationMatrix = new Mat4();

    public Entity(TexturedModel model) {
        this.model = model;
        updateTransformationMatrix();
    }

    public TexturedModel getModel() {
        return model;
    }

    public void setPosition(float x, float y, float z) {
        this.posX = x; this.posY = y; this.posZ = z;
        updateTransformationMatrix();
    }

    public void setRotation(float x, float y, float z) {
        this.rotX = x; this.rotY = y; this.rotZ = z;
        updateTransformationMatrix();
    }

    public void setScale(float x, float y, float z) {
        this.scaleX = x; this.scaleY = y; this.scaleZ = z;
        updateTransformationMatrix();
    }

    public void transformPosition(float dx, float dy, float dz) {
        this.posX += dx; this.posY += dy; this.posZ += dz;
        updateTransformationMatrix();
    }

    public void setColor(float r, float g, float b, float a) {
        this.red = r; this.green = g; this.blue = b; this.alpha = a;
    }

    /**
     * Re-calculates the matrix using our algebraically unrolled, hyper-fast GeomMath.
     * Uses the existing matrix memory block (dest = transformationMatrix).
     */
    public void updateTransformationMatrix() {
        GeomMath.createTransformationMatrix(
                posX, posY, posZ,
                rotX, rotY, rotZ,
                scaleX, scaleY, scaleZ,
                transformationMatrix
        );
    }

    public float getRed() { return red; }
    public float getGreen() { return green; }
    public float getBlue() { return blue; }
    public float getAlpha() { return alpha; }
}