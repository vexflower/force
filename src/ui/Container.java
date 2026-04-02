package ui;

import entity.Entity;
import environment.RendererManager;
import lang.Mat4;
import lang.GeomMath;
import renderer.RenderState;
import ui.scene.Scene;
import util.FastList;

/**
 * High-level abstract UI Container.
 * Manages the zero-GC child hierarchy, dirty-state propagation,
 * and absolute screen-space matrix calculations.
 */
public class Container extends FrameBufferObject {

    public float localX = 0f;
    public float localY = 0f;
    public float rotation = 0f;
    public float scaleX = 1f;
    public float scaleY = 1f;

    public final Mat4 renderTransform = new Mat4();
    public float absoluteX = 0f;
    public float absoluteY = 0f;

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

    // [NEW] Hook for subclasses (like Scene3D) to rebuild matrices
    protected void onResize(int w, int h) {}

    // [UPDATED] Override setSize to trigger the hook automatically
    public void setSize(int w, int h) {
        if (this.width != w || this.height != h) {
            this.width = w;
            this.height = h;
            this.isDirty = true;
            onResize(w, h);
        }
    }

    public void updateTransform(float parentAbsX, float parentAbsY, boolean forceUpdate) {
        // [NEW]: Lazy Init for FBO panels! If I am not the Root Window, I need an FBO.
        if (this.textureId == -1 && hardware.Display.getContentPane() != this && this.width > 0 && this.height > 0) {
            this.init();
        }

        boolean needsUpdate = this.isDirty || forceUpdate;

        if (needsUpdate) {
            this.absoluteX = parentAbsX + localX;
            this.absoluteY = parentAbsY + localY;

            GeomMath.createTransformationMatrix(
                    absoluteX + width / 2.0f, absoluteY + height / 2.0f, 0f, 0f, rotation,
                    (float) width * scaleX, (float) height * scaleY,
                    this.renderTransform
            );
            this.isDirty = false;
        }

        for (int i = 0; i < children.size(); i++) {
            children.get(i).updateTransform(this.absoluteX, this.absoluteY, needsUpdate);
        }
    }

    public void extract3DEntities(RenderState state) {
        for (int i = 0; i < children.size(); i++) {
            children.get(i).extract3DEntities(state);
        }
    }

    public void extractUIData(RenderState state) {
        // Queue this container if it IS an active FBO or textured Panel
        if (this.textureId != -1 && state.uiElementCount < 100) {
            int index = state.uiElementCount++;
            state.uiTextureIds[index] = this.textureId;
            this.renderTransform.store(state.uiTransforms, index * 16);
        }
        for (int i = 0; i < children.size(); i++) {
            children.get(i).extractUIData(state);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        for (int i = 0; i < children.size(); i++) children.get(i).destroy();
    }
}