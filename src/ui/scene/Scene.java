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
                            if (actualMesh == null) actualMesh = loader.MeshRegistry.get(meshId);

                            if (snap.entityCount == 0) {
                                snap.globalEntityOffset = state.totalEntities;
                            }

                            // Write DIRECTLY to this snapshot's isolated off-heap buffer
                            long finalPtr = snap.entityDataPtr + (snap.entityCount * 96L);

                            // 1. Blast the Matrix
                            ent.modelMatrix.store(finalPtr);

                            // 2. Write Texture & Flags via MemoryUtil (Offsets 64-76)
                            org.lwjgl.system.MemoryUtil.memPutFloat(finalPtr + 64, Float.intBitsToFloat(ent.material.diffuseTextureId));
                            org.lwjgl.system.MemoryUtil.memPutFloat(finalPtr + 68, Float.intBitsToFloat(actualMesh.vertexOffset));
                            org.lwjgl.system.MemoryUtil.memPutFloat(finalPtr + 72, 1.0f); // isVisible
                            org.lwjgl.system.MemoryUtil.memPutFloat(finalPtr + 76, ent.material.transparency < 1.0f ? 1.0f : 0.0f);

                            // 3. Write Material Properties (Offsets 80-92)
                            org.lwjgl.system.MemoryUtil.memPutFloat(finalPtr + 80, ent.material.celShade);
                            org.lwjgl.system.MemoryUtil.memPutFloat(finalPtr + 84, ent.material.reflectivity);
                            org.lwjgl.system.MemoryUtil.memPutFloat(finalPtr + 88, ent.material.shineDamper);
                            org.lwjgl.system.MemoryUtil.memPutFloat(finalPtr + 92, 0.0f); // padding

                            snap.entityCount++;
                            state.totalEntities++;
                            instanceCount++;
                        }
                    }

                    // Record the grouped command for the GPU
                    int groupIdx = snap.groupCount++;
                    assert actualMesh != null;
                    snap.groupFirstIndex[groupIdx] = actualMesh.firstIndex;
                    snap.groupIndexCounts[groupIdx] = actualMesh.indexCount;
                    snap.groupInstanceCounts[groupIdx] = instanceCount;
                }
            }
        }
        super.extract3DEntities(state);
    }

}