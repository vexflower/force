package mesh;

import lang.Vec3;
import org.lwjgl.system.MemoryUtil;
import java.util.Arrays;

/**
 * Pure Java, Off-Heap implementation of Quadric Error Metrics (QEM) Edge Collapse.
 * Features primitive edge-frequency hashing to lock UV seams and prevent mesh tearing.
 */
public class MeshOptimizer {

    private static class Quadric {
        float m00, m01, m02, m03;
        float m11, m12, m13;
        float m22, m23;
        float m33;

        public void add(Quadric q) {
            m00 += q.m00; m01 += q.m01; m02 += q.m02; m03 += q.m03;
            m11 += q.m11; m12 += q.m12; m13 += q.m13;
            m22 += q.m22; m23 += q.m23;
            m33 += q.m33;
        }

        public float evaluate(float x, float y, float z) {
            return (m00*x*x) + (2*m01*x*y) + (2*m02*x*z) + (2*m03*x) +
                    (m11*y*y) + (2*m12*y*z) + (2*m13*y) +
                    (m22*z*z) + (2*m23*z) + m33;
        }
    }

    public static void generateLODs(Mesh mesh) {
        System.out.println("\n[JavaOptimizer] Running QEM Decimation on: " + mesh.name);

        int currentLod = 0;
        mesh.lodDistancesSq[0] = 0f;
        float distanceThreshold = 50f;

        while (currentLod < 5) {
            int srcVertexCount = mesh.lodVertexCounts[currentLod];
            int srcIndexCount = mesh.lodIndexCounts[currentLod];

            if (srcIndexCount < 36) break; // Don't crush below 12 triangles

            int targetIndexCount = (int)(srcIndexCount * 0.5f);
            int nextLod = currentLod + 1;

            decimate(mesh, currentLod, nextLod, targetIndexCount);

            if (mesh.lodVertexCounts[nextLod] >= srcVertexCount * 0.9f) {
                MemoryUtil.nmemFree(mesh.lodVertexPtrs[nextLod]);
                MemoryUtil.nmemFree(mesh.lodIndexPtrs[nextLod]);
                break;
            }

            mesh.lodDistancesSq[nextLod] = distanceThreshold * distanceThreshold;
            mesh.lodCount++;

            distanceThreshold *= 2.0f;
            currentLod++;

            System.out.println("Generated LOD " + currentLod + " -> Verts: " + mesh.lodVertexCounts[currentLod] + ", Indices: " + mesh.lodIndexCounts[currentLod]);
        }
    }

    private static void decimate(Mesh mesh, int srcLod, int dstLod, int targetIndexCount) {
        long vPtr = mesh.lodVertexPtrs[srcLod];
        long iPtr = mesh.lodIndexPtrs[srcLod];
        int vertexCount = mesh.lodVertexCounts[srcLod];
        int indexCount = mesh.lodIndexCounts[srcLod];

        // --- PHASE 1A: INITIALIZE QUADRICS ---
        Quadric[] quadrics = new Quadric[vertexCount];
        for (int i = 0; i < vertexCount; i++) quadrics[i] = new Quadric();

        Vec3 p0 = new Vec3(), p1 = new Vec3(), p2 = new Vec3();
        Vec3 e1 = new Vec3(), e2 = new Vec3(), normal = new Vec3();

        for (int i = 0; i < indexCount; i += 3) {
            int i0 = MemoryUtil.memGetInt(iPtr + (i * 4L));
            int i1 = MemoryUtil.memGetInt(iPtr + ((i + 1) * 4L));
            int i2 = MemoryUtil.memGetInt(iPtr + ((i + 2) * 4L));

            getVertexPos(vPtr, i0, p0); getVertexPos(vPtr, i1, p1); getVertexPos(vPtr, i2, p2);

            Vec3.sub(p1, p0, e1); Vec3.sub(p2, p0, e2); Vec3.cross(e1, e2, normal);

            float area = normal.length();
            if (area > 0.00001f) normal.scale(1.0f / area);

            float a = normal.x, b = normal.y, c = normal.z;
            float d = -(a * p0.x + b * p0.y + c * p0.z);

            Quadric tq = new Quadric();
            tq.m00 = a*a*area; tq.m01 = a*b*area; tq.m02 = a*c*area; tq.m03 = a*d*area;
            tq.m11 = b*b*area; tq.m12 = b*c*area; tq.m13 = b*d*area;
            tq.m22 = c*c*area; tq.m23 = c*d*area;
            tq.m33 = d*d*area;

            quadrics[i0].add(tq); quadrics[i1].add(tq); quadrics[i2].add(tq);
        }

        // --- PHASE 1B: FAST EDGE SEAM DETECTION (Zero-GC Hash Map) ---
        int HASH_SIZE = Math.max(indexCount * 2, 100003); // Keep it sparse to avoid collisions
        long[] edgeKeys = new long[HASH_SIZE];
        int[] edgeCounts = new int[HASH_SIZE];
        Arrays.fill(edgeKeys, -1L);

        for (int i = 0; i < indexCount; i += 3) {
            int i0 = MemoryUtil.memGetInt(iPtr + (i * 4L));
            int i1 = MemoryUtil.memGetInt(iPtr + ((i + 1) * 4L));
            int i2 = MemoryUtil.memGetInt(iPtr + ((i + 2) * 4L));
            recordEdge(edgeKeys, edgeCounts, i0, i1);
            recordEdge(edgeKeys, edgeCounts, i1, i2);
            recordEdge(edgeKeys, edgeCounts, i2, i0);
        }

        // --- PHASE 2: GREEDY EDGE COLLAPSE ---
        int[] alias = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) alias[i] = i;

        boolean[] deletedTriangles = new boolean[indexCount / 3];
        int activeTriangles = indexCount / 3;
        boolean[] vertexLocked = new boolean[vertexCount];

        int maxPasses = 10;
        for (int pass = 0; pass < maxPasses && activeTriangles > targetIndexCount / 3; pass++) {
            for (int i = 0; i < vertexCount; i++) vertexLocked[i] = false;

            for (int i = 0; i < indexCount; i += 3) {
                int triIndex = i / 3;
                if (deletedTriangles[triIndex]) continue;

                // Read original indices to check edge seams correctly
                int orig0 = MemoryUtil.memGetInt(iPtr + (i * 4L));
                int orig1 = MemoryUtil.memGetInt(iPtr + ((i + 1) * 4L));
                int orig2 = MemoryUtil.memGetInt(iPtr + ((i + 2) * 4L));

                int i0 = getAlias(alias, orig0);
                int i1 = getAlias(alias, orig1);
                int i2 = getAlias(alias, orig2);

                if (i0 == i1 || i1 == i2 || i2 == i0) {
                    deletedTriangles[triIndex] = true;
                    activeTriangles--;
                    continue;
                }

                int bestU = -1, bestV = -1;
                float minError = Float.MAX_VALUE;

                // Edge 0-1
                if (!vertexLocked[i0] && !vertexLocked[i1]) {
                    float err = calculateEdgeError(vPtr, quadrics, i0, i1);
                    if (getEdgeCount(edgeKeys, edgeCounts, orig0, orig1) == 1) err += 100000.0f; // SEAM PENALTY!
                    if (err < minError) { minError = err; bestU = i0; bestV = i1; }
                }
                // Edge 1-2
                if (!vertexLocked[i1] && !vertexLocked[i2]) {
                    float err = calculateEdgeError(vPtr, quadrics, i1, i2);
                    if (getEdgeCount(edgeKeys, edgeCounts, orig1, orig2) == 1) err += 100000.0f; // SEAM PENALTY!
                    if (err < minError) { minError = err; bestU = i1; bestV = i2; }
                }
                // Edge 2-0
                if (!vertexLocked[i2] && !vertexLocked[i0]) {
                    float err = calculateEdgeError(vPtr, quadrics, i2, i0);
                    if (getEdgeCount(edgeKeys, edgeCounts, orig2, orig0) == 1) err += 100000.0f; // SEAM PENALTY!
                    if (err < minError) { minError = err; bestU = i2; bestV = i0; }
                }

                if (bestU != -1 && minError < 10.0f) {
                    alias[bestV] = bestU;
                    quadrics[bestU].add(quadrics[bestV]);

                    vertexLocked[bestU] = true;
                    vertexLocked[bestV] = true;

                    deletedTriangles[triIndex] = true;
                    activeTriangles--;
                }
            }
        }

        // --- PHASE 3: REBUILD COMPRESSED C-MEMORY BUFFERS ---
        int[] newVertexMapping = new int[vertexCount];
        int finalVertexCount = 0;

        long tempVPtr = MemoryUtil.nmemAlloc(vertexCount * 8L * 4L);
        long tempIPtr = MemoryUtil.nmemAlloc(indexCount * 4L);
        int finalIndexCount = 0;

        for (int i = 0; i < vertexCount; i++) {
            if (alias[i] == i) {
                newVertexMapping[i] = finalVertexCount;
                long srcOffset = vPtr + (i * 8L * 4L);
                long dstOffset = tempVPtr + (finalVertexCount * 8L * 4L);
                MemoryUtil.memCopy(srcOffset, dstOffset, 32L);
                finalVertexCount++;
            } else {
                newVertexMapping[i] = -1;
            }
        }

        for (int i = 0; i < indexCount; i += 3) {
            if (deletedTriangles[i / 3]) continue;

            int i0 = getAlias(alias, MemoryUtil.memGetInt(iPtr + (i * 4L)));
            int i1 = getAlias(alias, MemoryUtil.memGetInt(iPtr + ((i + 1) * 4L)));
            int i2 = getAlias(alias, MemoryUtil.memGetInt(iPtr + ((i + 2) * 4L)));

            if (i0 != i1 && i1 != i2 && i2 != i0) {
                MemoryUtil.memPutInt(tempIPtr + (finalIndexCount * 4L), newVertexMapping[i0]);
                MemoryUtil.memPutInt(tempIPtr + ((finalIndexCount + 1) * 4L), newVertexMapping[i1]);
                MemoryUtil.memPutInt(tempIPtr + ((finalIndexCount + 2) * 4L), newVertexMapping[i2]);
                finalIndexCount += 3;
            }
        }

        mesh.lodVertexCounts[dstLod] = finalVertexCount;
        mesh.lodIndexCounts[dstLod] = finalIndexCount;

        mesh.lodVertexPtrs[dstLod] = MemoryUtil.nmemAlloc(finalVertexCount * 8L * 4L);
        mesh.lodIndexPtrs[dstLod] = MemoryUtil.nmemAlloc(finalIndexCount * 4L);

        MemoryUtil.memCopy(tempVPtr, mesh.lodVertexPtrs[dstLod], finalVertexCount * 8L * 4L);
        MemoryUtil.memCopy(tempIPtr, mesh.lodIndexPtrs[dstLod], finalIndexCount * 4L);

        MemoryUtil.nmemFree(tempVPtr);
        MemoryUtil.nmemFree(tempIPtr);
    }

    // --- ZERO GC FAST HASHING HELPERS ---

    private static void recordEdge(long[] keys, int[] counts, int i0, int i1) {
        // Bit-pack the two indices into a single 64-bit long. Min/Max ensures (A,B) and (B,A) get the exact same key.
        long key = ((long) Math.min(i0, i1) << 32) | ((long) Math.max(i0, i1) & 0xFFFFFFFFL);

        // Fast Wang hash
        int hash = (int) ((key ^ (key >>> 32)) % keys.length);
        if (hash < 0) hash += keys.length;

        // Linear probing for collisions
        while (keys[hash] != -1L && keys[hash] != key) {
            hash = (hash + 1) % keys.length;
        }
        keys[hash] = key;
        counts[hash]++;
    }

    private static int getEdgeCount(long[] keys, int[] counts, int i0, int i1) {
        long key = ((long) Math.min(i0, i1) << 32) | ((long) Math.max(i0, i1) & 0xFFFFFFFFL);
        int hash = (int) ((key ^ (key >>> 32)) % keys.length);
        if (hash < 0) hash += keys.length;

        while (keys[hash] != -1L) {
            if (keys[hash] == key) return counts[hash];
            hash = (hash + 1) % keys.length;
        }
        return 0; // Should never happen logically, but safe fallback
    }

    private static int getAlias(int[] alias, int index) {
        while (alias[index] != index) {
            index = alias[index];
        }
        return index;
    }

    private static float calculateEdgeError(long vPtr, Quadric[] quadrics, int u, int v) {
        Quadric qMerge = new Quadric();
        qMerge.add(quadrics[u]);
        qMerge.add(quadrics[v]);

        Vec3 posU = new Vec3(), posV = new Vec3();
        getVertexPos(vPtr, u, posU);
        getVertexPos(vPtr, v, posV);

        float errU = qMerge.evaluate(posU.x, posU.y, posU.z);
        float errV = qMerge.evaluate(posV.x, posV.y, posV.z);

        float midX = (posU.x + posV.x) * 0.5f;
        float midY = (posU.y + posV.y) * 0.5f;
        float midZ = (posU.z + posV.z) * 0.5f;
        float errMid = qMerge.evaluate(midX, midY, midZ);

        return Math.min(errMid, Math.min(errU, errV));
    }

    private static void getVertexPos(long vPtr, int index, Vec3 dest) {
        long ptr = vPtr + (index * 8L * 4L);
        dest.x = MemoryUtil.memGetFloat(ptr);
        dest.y = MemoryUtil.memGetFloat(ptr + 4L);
        dest.z = MemoryUtil.memGetFloat(ptr + 8L);
    }
}