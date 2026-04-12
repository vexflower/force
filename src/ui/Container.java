package ui;

import lang.Mat4;
import lang.GeomMath;
import move.Move;
import renderer.RenderState;
import util.FastList;

public class Container {
    // Unique ID so the GPU can securely track this UI element's texture
    private static int nextId = 0;
    public final int id = nextId++;

    public int width, height;
    public float localX = 0f, localY = 0f;
    public float rotation = 0f, scaleX = 1f, scaleY = 1f;

    public final Mat4 renderTransform = new Mat4();
    public float absoluteX = 0f, absoluteY = 0f;

    public FastList<Container> children = new FastList<>();
    public boolean isDirty = true;

    // --- PURE DATA FLAGS (No Vulkan imports!) ---
    public boolean requiresOffscreen = false;
    public float bgR = 0f, bgG = 0f, bgB = 0f, bgA = 0f;

    // --- ZERO-GC PRE-ALLOCATED COMPONENTS ---
    public final Move posMove = new Move();
    public final Move rotMove = new Move();
    public final Move scaleMove = new Move();

    public Container() { this(0, 0); }

    public Container(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void add(Container child) {
        children.add(child);
        this.isDirty = true;
    }

    public void setPosition(float x, float y) {
        this.localX = x; this.localY = y; this.isDirty = true;
    }

    public void setSize(int w, int h) {
        if (this.width != w || this.height != h) {
            this.width = w; this.height = h; this.isDirty = true;
            onResize(w, h);
        }
    }

    public void setBackgroundColor(float r, float g, float b, float a) {
        this.bgR = r; this.bgG = g; this.bgB = b; this.bgA = a;
        // If it has a background, tell the Renderer it needs its own FBO pass!
        this.requiresOffscreen = true;
    }

    protected void onResize(int w, int h) {}

    public void update(float delta) {
        // Update Position
        if (posMove.active) {
            if (posMove.duration == -1f) {
                // Infinite velocity movement
                this.localX += posMove.endX * delta;
                this.localY += posMove.endY * delta;
                isDirty = true;
            } else {
                posMove.time += delta;
                float t = Math.min(posMove.time / posMove.duration, 1.0f);
                float eased = posMove.getEased(t);
                this.localX = posMove.startX + (posMove.endX - posMove.startX) * eased;
                this.localY = posMove.startY + (posMove.endY - posMove.startY) * eased;
                isDirty = true;
                if (t >= 1.0f) posMove.active = false;
            }
        }

        // Update Rotation
        if (rotMove.active) {
            if (rotMove.duration == -1f) {
                this.rotation += rotMove.endX * delta;
                isDirty = true;
            } else {
                rotMove.time += delta;
                float t = Math.min(rotMove.time / rotMove.duration, 1.0f);
                float eased = rotMove.getEased(t);
                this.rotation = rotMove.startX + (rotMove.endX - rotMove.startX) * eased;
                isDirty = true;
                if (t >= 1.0f) rotMove.active = false;
            }
        }

        // Update Scale
        if (scaleMove.active) {
            scaleMove.time += delta;
            float t = Math.min(scaleMove.time / scaleMove.duration, 1.0f);
            float eased = scaleMove.getEased(t);
            this.scaleX = scaleMove.startX + (scaleMove.endX - scaleMove.startX) * eased;
            this.scaleY = scaleMove.startY + (scaleMove.endY - scaleMove.startY) * eased;
            isDirty = true;
            if (t >= 1.0f) scaleMove.active = false;
        }

        for (int i = 0; i < children.size(); i++) {
            children.get(i).update(delta);
        }
    }

    public void updateTransform(float parentAbsX, float parentAbsY, boolean forceUpdate) {
        boolean needsUpdate = this.isDirty || forceUpdate;

        if (needsUpdate) {
            this.absoluteX = parentAbsX + localX;
            this.absoluteY = parentAbsY + localY;
            GeomMath.createTransformationMatrix(absoluteX + width / 2.0f, absoluteY + height / 2.0f, 0f, 0f, rotation, (float) width * scaleX, (float) height * scaleY, this.renderTransform);
            this.isDirty = false;
        }

        for (int i = 0; i < children.size(); i++) {
            children.get(i).updateTransform(this.absoluteX, this.absoluteY, needsUpdate);
        }
    }

    // Inside Container.java -> replace the current extract3DEntities method
    public void extract3DEntities(RenderState state) {
        // If a standard panel/container has a background, it needs an FBO generated!
        if (this.requiresOffscreen && !(this instanceof ui.scene.Scene)) {
            if (state.snapshotCount < state.snapshots.length) {
                renderer.SceneSnapshot snap = state.snapshots[state.snapshotCount++];
                snap.containerId = this.id;
                snap.isOffscreen = true;
                snap.width = this.width;
                snap.height = this.height;
                snap.bgR = this.bgR; snap.bgG = this.bgG; snap.bgB = this.bgB; snap.bgA = this.bgA;
                snap.entityCount = 0; // It's just a UI background, no 3D entities
            }
        }

        for (int i = 0; i < children.size(); i++) {
            children.get(i).extract3DEntities(state);
        }
    }

    public void extractUIData(RenderState state) {
        if (this.requiresOffscreen && state.uiElementCount < 100) {
            state.uiElementCount++;
            state.uiTextureIds.add(this.id);
            this.renderTransform.store(state.uiTransforms);
        }
        for (int i = 0; i < children.size(); i++) {
            children.get(i).extractUIData(state);
        }
    }

    public void destroy() {
        for (int i = 0; i < children.size(); i++) children.get(i).destroy();
    }
}