package renderer;

import lang.Mat4;

/**
 * A flat, Zero-GC data container representing a single frame of gameplay.
 */
public class RenderState {

    // For now, just our single spinning triangle.
    // Later, this will be an array of Mat4s for MAX_ENTITIES.
    public final Mat4 spinningTriangleTransform = new Mat4();

    // Add other frame-specific data here later (e.g., Camera View Matrix, UI text)
}