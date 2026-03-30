package ui;

import lang.Mat4;
import lang.GeomMath;
import util.FastList;

/**
 * High-level abstract UI Container.
 * Manages the zero-GC child hierarchy, dirty-state propagation,
 * and absolute screen-space matrix calculations.
 */
public abstract class Container extends FrameBufferObject {

    // --- LOCAL TRANSFORM (Relative to Parent) ---
    public float localX = 0f;
    public float localY = 0f;
    public float rotation = 0f;
    public float scaleX = 1f;
    public float scaleY = 1f;

    // --- ABSOLUTE TRANSFORM (Sent to the GPU) ---
    // Pre-allocated once at startup. Zero-GC.
    public final Mat4 absoluteTransform = new Mat4();

    // A single thread-safe scratchpad for the Logic Thread to prevent 'new Mat4()' spam
    private static final Mat4 SCRATCH_MATRIX = new Mat4();

    public FastList<Container> children = new FastList<>();
    public boolean isDirty = true;

    protected Container() {
        super(0, 0);
    }

    protected Container(int width, int height) {
        super(width, height);
    }

    public void add(Container child) {
        children.add(child);
        this.isDirty = true;
    }

    /**
     * Logic update loop.
     */
    public void update(float delta) {
        // Custom logic for panels can go here (animations, color fades, etc.)

        for (int i = 0; i < children.size(); i++) {
            children.get(i).update(delta);
        }
    }

    // --- Add these Swing-like setters ---
    public void setPosition(float x, float y) {
        this.localX = x;
        this.localY = y;
        this.isDirty = true;
    }

    public void setSize(int w, int h) {
        this.width = w;
        this.height = h;
        this.isDirty = true;
    }

    /**
     * Cascades down the tree, calculating the final 2D matrix for the shader.
     * @param parentTransform The absolute matrix of the container holding this one.
     * @param forceUpdate True if the parent moved, forcing this child to recalculate.
     */
    public void updateTransform(Mat4 parentTransform, boolean forceUpdate) {
        boolean needsUpdate = this.isDirty || forceUpdate;

        if (needsUpdate) {
            // [THE SWING FIX]
            // Mesh.SQUARE's origin is center-based.
            // We shift it by half its width/height to make the origin Top-Left.
            float centerX = localX + (width / 2.0f);
            float centerY = localY + (height / 2.0f);

            GeomMath.createTransformationMatrix(
                    centerX, centerY,
                    0f, 0f, rotation,
                    (float) width * scaleX, (float) height * scaleY, // Scale 1x1 quad into pixel dimensions
                    SCRATCH_MATRIX
            );

            // 2. Multiply by the parent to get the absolute screen position
            if (parentTransform != null) {
                // Zero-allocation multiplication: absolute = parent * local [cite: 446]
                parentTransform.mul(SCRATCH_MATRIX, this.absoluteTransform);
            } else {
                // If this is the Root node (the Scene), its local is its absolute [cite: 411]
                this.absoluteTransform.load(SCRATCH_MATRIX);
            }

            this.isDirty = false;
        }

        // 3. Propagate down the tree.
        // If this node updated, we MUST force all children to update their relative positions!
        for (int i = 0; i < children.size(); i++) {
            children.get(i).updateTransform(this.absoluteTransform, needsUpdate);
        }
    }



    /**
     * Recursively destroys all Vulkan FBOs in this tree.
     */
    @Override
    public void destroy() {
        super.destroy(); // Destroys this container's FBO
        for (int i = 0; i < children.size(); i++) {
            children.get(i).destroy();
        }
    }
}