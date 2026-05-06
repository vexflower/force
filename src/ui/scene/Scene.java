package ui.scene;

import lang.Mat4;
import ui.Container;
import util.FastList;

public class Scene extends Container {

    public FastList<entity.Entity> activeEntities = new FastList<>();
    private final Mat4 orthoProjection = new Mat4();

    private int currentWidth;
    private int currentHeight;
    private Container contentPane;
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
        this.children.clear();
        this.contentPane = panel;
        this.add(panel);
        this.isDirty = true;
    }

    protected void onResize(int width, int height) {}

    public void addEntity(entity.Entity ent) {
        activeEntities.add(ent);
    }

    @Override
    public void extract3DEntities(renderer.RenderState state) {
        if (state.snapshotCount < state.snapshots.length) {

            renderer.SceneSnapshot snap = state.snapshots[state.snapshotCount++];
            snap.containerId = this.id;
            snap.isOffscreen = this.requiresOffscreen;
            snap.width = this.width;
            snap.height = this.height;
            snap.bgR = this.bgR; snap.bgG = this.bgG; snap.bgB = this.bgB; snap.bgA = this.bgA;

            if (!activeEntities.isEmpty() && snap.entityCount == 0) {
                snap.globalEntityOffset = state.totalEntities;
            }

            // ... [Inside extract3DEntities, inside the activeEntities loop] ...
            for (int i = 0; i < activeEntities.size(); i++) {
                entity.Entity ent = activeEntities.get(i);
                ent.modelMatrix.store(snap.entityData);

                int meshId = environment.RendererManager.meshIds.get(ent.id);
                mesh.Mesh baseMesh = loader.MeshRegistry.get(meshId); // Grab the Mesh object

                snap.entityData.add(Float.intBitsToFloat(ent.material.diffuseTextureId));

                // ---> THE FIX: Hand the Compute Shader the exact Meshlet locations! <---
                snap.entityData.add(Float.intBitsToFloat(baseMesh.globalMeshletOffset));
                snap.entityData.add(Float.intBitsToFloat(baseMesh.meshletCount));
                // ---> THE FIX: Add the missing isVisible float!
                snap.entityData.add(1.0f); // will be swapped to isVisible

                snap.entityData.add(ent.isOccluder);
                snap.entityData.add(ent.material.celShade);
                snap.entityData.add(ent.material.reflectivity);
                snap.entityData.add(ent.material.shineDamper);

                snap.entityCount++;
                state.totalEntities++;
            }
        }
        super.extract3DEntities(state);
    }
}