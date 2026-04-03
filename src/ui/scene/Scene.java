package ui.scene;

import lang.Mat4;
import renderer.RenderState;
import ui.Container;
import util.FastList;

public class Scene extends Container {

    // Replaced IntList with FastList<Entity>
    public FastList<entity.Entity> activeEntities = new FastList<>();
    private final Mat4 orthoProjection = new Mat4();

    // [NEW] Track screen dimensions to detect resizes
    private int currentWidth;
    private int currentHeight;

    // [NEW] The Root Swing Panel!
    private Container contentPane;

    // [NEW] The pure origin point to start the UI math
    private static final Mat4 ROOT_PIXEL_MATRIX = new Mat4();

    public Scene() {
        super();
    }

    public Scene(int width, int height) {
        super(width, height);
        this.currentWidth = width;
        this.currentHeight = height;
        orthoProjection.ortho(0, width, height, 0, -1f, 1f);
    }

    public void setContentPane(Container panel) {
        this.children.clear(); // Remove old pane
        this.contentPane = panel;
        this.add(panel);
        this.isDirty = true;
    }

    // [NEW] Hook for subclasses (like Scene3D) to update their Perspective matrices
    protected void onResize(int width, int height) {}

    public void addEntity(entity.Entity ent) {
        activeEntities.add(ent);
    }

    @Override
    public void extract3DEntities(renderer.RenderState state) {
        if (state.snapshotCount < state.snapshots.length) {
            renderer.SceneSnapshot snap = state.snapshots[state.snapshotCount++];

            // Pure data transfer. The Renderer will handle the actual VRAM.
            snap.containerId = this.id;
            snap.isOffscreen = this.requiresOffscreen;
            snap.width = this.width;
            snap.height = this.height;
            snap.bgR = this.bgR; snap.bgG = this.bgG; snap.bgB = this.bgB; snap.bgA = this.bgA;

            for (int i = 0; i < activeEntities.size(); i++) {
                if (snap.entityCount >= snap.meshIds.length) break;

                entity.Entity ent = activeEntities.get(i);
                int idx = snap.entityCount++;
                snap.meshIds[idx] = environment.RendererManager.meshIds.get(ent.id);
                snap.textureIds[idx] = environment.RendererManager.diffuseTextureIds.get(ent.id);
                ent.mvpMatrix.store(snap.transforms, idx * 16);
            }
        }
        super.extract3DEntities(state);
    }

}