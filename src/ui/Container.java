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

    // --- ABSOLUTE TRANSFORM (Sent to Children) ---
    public final Mat4 absoluteTransform = new Mat4();

    // --- RENDER TRANSFORM (Sent to the GPU) ---
    public final Mat4 renderTransform = new Mat4(); // [NEW]

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
            // 1. The Hierarchy Matrix (Top-Left origin, no scaling). Sent to children.
            GeomMath.createTransformationMatrix(
                    localX, localY, 0f, 0f, rotation,
                    1f, 1f, // NO WIDTH/HEIGHT SCALING HERE
                    SCRATCH_MATRIX
            );

            if (parentTransform != null) {
                parentTransform.mul(SCRATCH_MATRIX, this.absoluteTransform);
            } else {
                this.absoluteTransform.load(SCRATCH_MATRIX);
            }

            // 2. The Render Matrix (Center-shifted and scaled). Sent to the GPU.
            GeomMath.createTransformationMatrix(
                    width / 2.0f, height / 2.0f, 0f, 0f, 0f,
                    (float) width * scaleX, (float) height * scaleY,
                    SCRATCH_MATRIX
            );

            // absoluteTransform * centerScale = Final GPU Matrix
            this.absoluteTransform.mul(SCRATCH_MATRIX, this.renderTransform);

            this.isDirty = false;
        }

        // 3. Propagate the clean top-left anchor to the children
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