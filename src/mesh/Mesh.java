package mesh;

import org.lwjgl.system.MemoryUtil;
import java.io.File;

public class Mesh {
    public static final String MESH_DIRECTORY = System.getProperty("user.home") + File.separator + "framework" + File.separator + "meshes";

    public String name;
    public int vaoId;

    // --- PURE OFF-HEAP LOD TRACKERS ---
    public int lodCount = 1;

    // We only store the raw 64-bit C-Pointers! Zero GC overhead.
    public long[] lodVertexPtrs = new long[16]; // Points to interleaved data: [X,Y,Z, U,V, NX,NY,NZ]
    public long[] lodIndexPtrs = new long[16];  // Points to int indices

    // We must track the sizes manually since C-Pointers don't have a .length
    public int[] lodVertexCounts = new int[16];
    public int[] lodIndexCounts = new int[16];
    public float[] lodDistancesSq = new float[16];

    // Bindless Pointers for the GPU SSBO
    public int[] lodVertexOffsets = new int[16];
    public int[] lodFirstIndices = new int[16];

    public static Mesh SQUARE;
    public static Mesh FLOOR;
    public static Mesh CUBE;

    public Mesh(String name) {
        this.name = name;
    }

    public static void initPrimitives() {
        System.out.println("Building off-heap primitive meshes...");

        SQUARE = new Mesh("square");
        buildPrimitiveOffHeap(SQUARE,
                new float[] { -0.5f, -0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f, 0.5f, 0f, 0.5f, -0.5f, 0f },
                new float[] { 0,0, 0,1, 1,1, 1,0 },
                new float[] { 0,0,1, 0,0,1, 0,0,1, 0,0,1 },
                new int[] { 0, 1, 3, 3, 1, 2 }
        );

        CUBE = new Mesh("cube");
        buildPrimitiveOffHeap(CUBE,
                new float[] {
                        -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
                        0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
                        -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f,
                        -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f,
                        0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f,
                        -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f
                },
                new float[] {
                        1,1, 1,0, 0,0, 0,1, 1,1, 1,0, 0,0, 0,1, 1,1, 1,0, 0,0, 0,1,
                        1,1, 1,0, 0,0, 0,1, 1,1, 1,0, 0,0, 0,1, 1,1, 1,0, 0,0, 0,1
                },
                new float[] {
                        0,0,1, 0,0,1, 0,0,1, 0,0,1, 0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1,
                        0,1,0, 0,1,0, 0,1,0, 0,1,0, 0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0,
                        1,0,0, 1,0,0, 1,0,0, 1,0,0, -1,0,0, -1,0,0, -1,0,0, -1,0,0
                },
                new int[] {
                        0, 3, 1, 3, 2, 1, 4, 7, 5, 7, 6, 5, 8, 11, 9, 11, 10, 9,
                        12, 15, 13, 15, 14, 13, 16, 19, 17, 19, 18, 17, 20, 23, 21, 23, 22, 21
                }
        );

        FLOOR = new Mesh("floor");
        buildPrimitiveOffHeap(FLOOR,
                new float[] { -0.5f, 0f, -0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f },
                new float[] { 0,0, 0,200, 200,200, 200,0 },
                new float[] { 0,1,0, 0,1,0, 0,1,0, 0,1,0 },
                new int[] { 0, 3, 1, 3, 2, 1 }
        );

        loader.MeshLoader.loadMesh(SQUARE);
        loader.MeshLoader.loadMesh(CUBE);
        loader.MeshLoader.loadMesh(FLOOR);
    }

    private static void buildPrimitiveOffHeap(Mesh m, float[] p, float[] t, float[] n, int[] ind) {
        m.lodVertexCounts[0] = p.length / 3;
        m.lodIndexCounts[0] = ind.length;
        m.lodDistancesSq[0] = 0f;

        // Allocate memory (8 floats per vertex)
        m.lodVertexPtrs[0] = MemoryUtil.nmemAlloc(m.lodVertexCounts[0] * 8L * 4L);
        m.lodIndexPtrs[0] = MemoryUtil.nmemAlloc(m.lodIndexCounts[0] * 4L);

        // Interleave the data into the C-Pointer
        for (int i = 0; i < m.lodVertexCounts[0]; i++) {
            long vPtr = m.lodVertexPtrs[0] + (i * 8L * 4L);
            MemoryUtil.memPutFloat(vPtr, p[i*3]);
            MemoryUtil.memPutFloat(vPtr + 4, p[i*3+1]);
            MemoryUtil.memPutFloat(vPtr + 8, p[i*3+2]);
            MemoryUtil.memPutFloat(vPtr + 12, t[i*2]);
            MemoryUtil.memPutFloat(vPtr + 16, t[i*2+1]);
            MemoryUtil.memPutFloat(vPtr + 20, n[i*3]);
            MemoryUtil.memPutFloat(vPtr + 24, n[i*3+1]);
            MemoryUtil.memPutFloat(vPtr + 28, n[i*3+2]);
        }

        // Copy indices
        for (int i = 0; i < ind.length; i++) {
            MemoryUtil.memPutInt(m.lodIndexPtrs[0] + (i * 4L), ind[i]);
        }
    }

    public void freeMemory() {
        for (int i = 0; i < lodCount; i++) {
            MemoryUtil.nmemFree(lodVertexPtrs[i]);
            MemoryUtil.nmemFree(lodIndexPtrs[i]);
        }
    }
}