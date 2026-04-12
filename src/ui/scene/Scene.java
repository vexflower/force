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

            // C-Memory Hash checking!
            util.CLongList processedGroupHashes = new util.CLongList(100);

            // --- DEBUG FLAG ---
            boolean FORCE_REVERSE_LOD = false;

            for (int i = 0; i < activeEntities.size(); i++) {
                entity.Entity baseEnt = activeEntities.get(i);
                int meshId = environment.RendererManager.meshIds.get(baseEnt.id);
                mesh.Mesh baseMesh = loader.MeshRegistry.get(meshId);

                // 1. Calculate Distance
                float distSq = 0f;
                if (this instanceof Scene3D) {
                    entity.Camera cam = ((Scene3D)this).currentCamera;
                    float dx = baseEnt.x - cam.posX;
                    float dy = baseEnt.y - cam.posY;
                    float dz = baseEnt.z - cam.posZ;
                    distSq = (dx * dx) + (dy * dy) + (dz * dz);
                }

                // 2. Determine LOD
                int targetLod = 0;
                for (int l = 1; l < baseMesh.lodCount; l++) {
                    if (distSq >= baseMesh.lodDistancesSq[l]) targetLod = l;
                    else break;
                }

                // --- APPLY REVERSAL IF FLAG IS TRUE ---
                if (FORCE_REVERSE_LOD && baseMesh.lodCount > 1) {
                    targetLod = (baseMesh.lodCount - 1) - targetLod;
                }

                long groupHash = ((long) meshId << 32) | targetLod;

                if (!processedGroupHashes.contains(groupHash)) {
                    processedGroupHashes.add(groupHash);
                    int instanceCount = 0;

                    for (int j = i; j < activeEntities.size(); j++) {

                        // THE FIX: Restored the missing entity extraction and inner distance math!
                        entity.Entity ent = activeEntities.get(j);
                        int entMeshId = environment.RendererManager.meshIds.get(ent.id);

                        if (entMeshId == meshId) {
                            float entDistSq = 0f;
                            if (this instanceof Scene3D) {
                                entity.Camera cam = ((Scene3D)this).currentCamera;
                                float dx = ent.x - cam.posX;
                                float dy = ent.y - cam.posY;
                                float dz = ent.z - cam.posZ;
                                entDistSq = (dx * dx) + (dy * dy) + (dz * dz);
                            }

                            int entLod = 0;
                            for (int l = 1; l < baseMesh.lodCount; l++) {
                                if (entDistSq >= baseMesh.lodDistancesSq[l]) entLod = l;
                                else break;
                            }

                            if (FORCE_REVERSE_LOD && baseMesh.lodCount > 1) {
                                entLod = (baseMesh.lodCount - 1) - entLod;
                            }

                            // NOW entLod and targetLod exist and can be compared!
                            if (entLod == targetLod) {
                                if (snap.entityCount == 0) snap.globalEntityOffset = state.totalEntities;

                                // NO MORE OFFSET MATH! Just sequential memory pushing!
                                ent.modelMatrix.store(snap.entityData);

                                snap.entityData.add(Float.intBitsToFloat(ent.material.diffuseTextureId));
                                snap.entityData.add(Float.intBitsToFloat(baseMesh.lodVertexOffsets[targetLod]));
                                snap.entityData.add(1.0f);
                                snap.entityData.add(ent.material.transparency < 1.0f ? 1.0f : 0.0f);

                                snap.entityData.add(ent.material.celShade);
                                snap.entityData.add(ent.material.reflectivity);
                                snap.entityData.add(ent.material.shineDamper);
                                snap.entityData.add((float) targetLod); // Debug padding

                                snap.entityCount++;
                                state.totalEntities++;
                                instanceCount++;
                            }
                        }
                    }

                    // NO MORE MDI OFFSET MATH!
                    snap.indirectData.add(baseMesh.lodIndexCounts[targetLod]);
                    snap.indirectData.add(instanceCount);
                    snap.indirectData.add(baseMesh.lodFirstIndices[targetLod]);
                    snap.indirectData.add(0);
                    snap.indirectData.add(snap.entityCount - instanceCount);

                    snap.commandCount++;
                }
            }

            // CRITICAL: Free the hash list so it doesn't leak!
            processedGroupHashes.free();
        }
        super.extract3DEntities(state);
    }
}