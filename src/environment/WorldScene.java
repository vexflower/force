package environment;

import lang.GeomMath;
import lang.Mat4;
import model.Mesh;
import resources.Resources;

public class WorldScene extends Scene {

    private int myBratSquareId;
    private float currentRotation = 0f;
    private Mat4 tempMatrix = new Mat4(); // Prevents GC allocations during rotation

    @Override
    public void init(long commandPool) {
        // 1. Load the texture to the GPU
        int bratTextureId = Resources.loadTexture("images/brat.png", commandPool);

        // 2. Spawn the Entity
        myBratSquareId = addEntity();

        // 3. Assign the Mesh and Texture to the flat arrays
        RendererManager.meshIds.set(myBratSquareId, Mesh.SQUARE.vaoId);
        RendererManager.diffuseTextureIds.set(myBratSquareId, bratTextureId);
    }

    @Override
    public void update(float delta) {
        // Rotate the square a bit every frame
        currentRotation += GeomMath.toRadians(90f * delta); // 90 degrees per second

        // Calculate the new Matrix
        tempMatrix.identity();
        tempMatrix.translate(0.0f, 0.0f, 0.5f); // Push it into the screen a bit
        tempMatrix.rotateY(currentRotation);    // Spin it!

        // Write the 16 floats into the massive RendererManager array
        int offset = myBratSquareId * 16;
        RendererManager.transforms.set(offset, tempMatrix.m00);
        RendererManager.transforms.set(offset + 1, tempMatrix.m01);
        // ... (We will write a helper to dump a Mat4 into a FloatList cleanly later)
        // For now, let's just cheat and add a helper in FloatList, or just write them out.
        tempMatrix.storeIntoFloatList(RendererManager.transforms, offset);
    }
}