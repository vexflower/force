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

            snap.containerId = this.id;
            snap.isOffscreen = this.requiresOffscreen;
            snap.width = this.width;
            snap.height = this.height;
            snap.bgR = this.bgR; snap.bgG = this.bgG; snap.bgB = this.bgB; snap.bgA = this.bgA;

            // Brutally fast O(N^2) grouper for Phase 1.
            // We find all unique meshes and batch them together!
            util.IntList processedMeshIds = new util.IntList(100);

            for (int i = 0; i < activeEntities.size(); i++) {
                int meshId = environment.RendererManager.meshIds.get(activeEntities.get(i).id);

                // If we haven't processed this specific Mesh yet...
                if (!processedMeshIds.contains(meshId)) {
                    processedMeshIds.add(meshId);

                    int instanceCount = 0;
                    model.Mesh actualMesh = null; // In a real system, fetch from a MeshRegistry using the ID

                    // Find all entities sharing this mesh
                    for (int j = i; j < activeEntities.size(); j++) {
                        entity.Entity ent = activeEntities.get(j);
                        int entMeshId = environment.RendererManager.meshIds.get(ent.id);

                        if (entMeshId == meshId) {
                            if (actualMesh == null) {
                                // Fallback for your current setup to get the offsets
                                actualMesh = (meshId == model.Mesh.CUBE.vaoId) ? model.Mesh.CUBE : model.Mesh.SQUARE; // Update this to fetch properly!
                            }

                            int texId = environment.RendererManager.diffuseTextureIds.get(ent.id);
                            int offset = snap.entityCount * 24;

                            // 1. Pack Matrix (16 floats)
                            ent.mvpMatrix.store(snap.entityData, offset);

                            // 2. Pack Integers as bitwise Floats (Zero GC)
                            snap.entityData[offset + 16] = Float.intBitsToFloat(texId);
                            snap.entityData[offset + 17] = Float.intBitsToFloat(actualMesh.vertexOffset);

                            // 3. Padding to maintain 32-byte struct alignment
                            snap.entityData[offset + 18] = 0f;
                            snap.entityData[offset + 19] = 0f;
                            snap.entityData[offset + 20] = 0f;
                            snap.entityData[offset + 21] = 0f;
                            snap.entityData[offset + 22] = 0f;
                            snap.entityData[offset + 23] = 0f;

                            snap.entityCount++;
                            instanceCount++;
                        }
                    }

                    // Record the grouped command for the GPU
                    int groupIdx = snap.groupCount++;
                    snap.groupFirstIndex[groupIdx] = actualMesh.firstIndex;
                    snap.groupIndexCounts[groupIdx] = actualMesh.indexCount;
                    snap.groupInstanceCounts[groupIdx] = instanceCount;
                }
            }
        }
        super.extract3DEntities(state);
    }

}