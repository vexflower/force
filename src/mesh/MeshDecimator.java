package mesh;

import org.lwjgl.system.MemoryUtil;
import java.util.HashMap;

public class MeshDecimator {

    public static void generateLODs(Mesh mesh) {
        System.out.println("Procedurally generating LODs for: " + mesh.name);

        int currentLod = 0;
        mesh.lodDistancesSq[0] = 0f;

        // THE FIX: Start with a VERY fine grid relative to the 1.0 unit cube.
        // 0.05 means the cube is divided into 20x20x20 cells.
        float gridSize = 0.05f;
        float distanceThreshold = 50f;

        while (currentLod < 15) {
            int prevVertexCount = mesh.lodVertexCounts[currentLod];
            if (prevVertexCount <= 8) break;

            int nextLod = currentLod + 1;
            clusterVertices(mesh, currentLod, nextLod, gridSize);

            // If the vertex count didn't drop by at least 10%, stop generating LODs.
            // This prevents generating identical LODs or getting stuck in a loop.
            if (mesh.lodVertexCounts[nextLod] >= prevVertexCount * 0.9f) {
                // Free the memory we just allocated for the useless LOD
                MemoryUtil.nmemFree(mesh.lodVertexPtrs[nextLod]);
                MemoryUtil.nmemFree(mesh.lodIndexPtrs[nextLod]);
                break;
            }

            mesh.lodDistancesSq[nextLod] = distanceThreshold * distanceThreshold;
            mesh.lodCount++;

            // Increase grid size more gradually to preserve shape longer
            gridSize *= 1.25f;
            distanceThreshold *= 1.5f;
            currentLod++;

            System.out.println("Generated LOD " + currentLod + " (" + mesh.lodVertexCounts[currentLod] + " vertices)");
        }
    }

    private static void clusterVertices(Mesh mesh, int srcLod, int dstLod, float gridSize) {
        long srcVertexPtr = mesh.lodVertexPtrs[srcLod];
        long srcIndexPtr = mesh.lodIndexPtrs[srcLod];
        int srcVertexCount = mesh.lodVertexCounts[srcLod];
        int srcIndexCount = mesh.lodIndexCounts[srcLod];

        HashMap<String, Integer> cellRemap = new HashMap<>();

        // THE FIX: Use C-Memory for the temporary calculation!
        util.CFloatList newVertices = new util.CFloatList(srcVertexCount * 8);
        int[] indexMap = new int[srcVertexCount];
        int newVertexCount = 0;

        for (int i = 0; i < srcVertexCount; i++) {
            long vPtr = srcVertexPtr + (i * 8L * 4L);
            float px = MemoryUtil.memGetFloat(vPtr);
            float py = MemoryUtil.memGetFloat(vPtr + 4);
            float pz = MemoryUtil.memGetFloat(vPtr + 8);

            int gridX = Math.round(px / gridSize);
            int gridY = Math.round(py / gridSize);
            int gridZ = Math.round(pz / gridSize);
            String hash = gridX + "," + gridY + "," + gridZ;

            if (!cellRemap.containsKey(hash)) {
                cellRemap.put(hash, newVertexCount);
                for(int offset = 0; offset < 32; offset += 4) {
                    newVertices.add(MemoryUtil.memGetFloat(vPtr + offset));
                }
                indexMap[i] = newVertexCount;
                newVertexCount++;
            } else {
                indexMap[i] = cellRemap.get(hash);
            }
        }

        util.CIntList newInd = new util.CIntList(srcIndexCount);
        for (int i = 0; i < srcIndexCount; i += 3) {
            int v0 = indexMap[MemoryUtil.memGetInt(srcIndexPtr + (i * 4L))];
            int v1 = indexMap[MemoryUtil.memGetInt(srcIndexPtr + ((i+1) * 4L))];
            int v2 = indexMap[MemoryUtil.memGetInt(srcIndexPtr + ((i+2) * 4L))];

            if (v0 != v1 && v1 != v2 && v2 != v0) {
                newInd.add(v0); newInd.add(v1); newInd.add(v2);
            }
        }

        mesh.lodVertexCounts[dstLod] = newVertexCount;
        mesh.lodIndexCounts[dstLod] = newInd.size();

        mesh.lodVertexPtrs[dstLod] = MemoryUtil.nmemAlloc(newVertexCount * 8L * 4L);
        mesh.lodIndexPtrs[dstLod] = MemoryUtil.nmemAlloc(newInd.size() * 4L);

        // THE FIX: Direct C-to-C Memory Copy!
        MemoryUtil.memCopy(newVertices.address(), mesh.lodVertexPtrs[dstLod], newVertexCount * 8L * 4L);
        MemoryUtil.memCopy(newInd.address(), mesh.lodIndexPtrs[dstLod], newInd.size() * 4L);

        // CRITICAL: Prevent the temporary lists from leaking!
        newVertices.free();
        newInd.free();
    }
}