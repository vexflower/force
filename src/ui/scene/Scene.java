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
    public void update(float delta) {
        int dw = hardware.Display.getWidth();
        int dh = hardware.Display.getHeight();

        // If this is the Main Screen, constantly check for window resizes!
        if (this.textureId == -1 && (dw != currentWidth || dh != currentHeight)) {
            currentWidth = dw;
            currentHeight = dh;
            this.width = dw;
            this.height = dh;
            if (contentPane != null) contentPane.setSize(dw, dh);
            onResize(dw, dh); // Recalculates the Matrix!
        }

        // Pass the tick down to the UI children
        super.update(delta);

        // Process UI absolute placement
        this.updateTransform(0, 0, false);
    }

    @Override
    public void extract3DEntities(RenderState state) {
        // Grab a fresh snapshot for this specific scene
        if (state.snapshotCount < state.snapshots.length) {
            renderer.SceneSnapshot snap = state.snapshots[state.snapshotCount++];

            // Link the FBO if this isn't the root window
            snap.fboReference = (this.textureId != -1) ? this : null;

            for (int i = 0; i < activeEntities.size(); i++) {
                entity.Entity ent = activeEntities.get(i);
                int idx = snap.entityCount++;
                snap.meshIds[idx] = environment.RendererManager.meshIds.get(ent.id);
                snap.textureIds[idx] = environment.RendererManager.diffuseTextureIds.get(ent.id);

                // Copy the matrix directly from the entity
                ent.mvpMatrix.store(snap.transforms, idx * 16);
            }
        }

        // Pass the call down to children so nested FBOs get extracted too!
        super.extract3DEntities(state);
    }

}