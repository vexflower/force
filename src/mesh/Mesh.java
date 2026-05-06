package mesh;

import java.io.File;

public class Mesh {
    public static final String MESH_DIRECTORY = System.getProperty("user.home") + File.separator + "framework" + File.separator + "meshes";

    // ---> THE FIX: Add a global static counter <---
    private static int globalVaoCounter = 0;

    public String name;
    public final int vaoId; // Make it final so it can never be overwritten!

    // General Geometry bounds (Used for the UI 2D Pass)
    public int globalVertexOffset = 0;
    public int globalIndexOffset = 0;
    public int indexCount = 0;

    // AAA Meshlet Vault bounds (Used for the 3D Pass)
    public int globalMeshletOffset = 0;
    public int meshletCount = 0;

    public static Mesh SQUARE;
    public static Mesh FLOOR;
    public static Mesh CUBE;
    public static Mesh QUAD;

    public Mesh(String name) {
        this.name = name;
        this.vaoId = globalVaoCounter++; // Assigns 0, then 1, then 2 automatically
    }

    public static void initPrimitives() {
        System.out.println("Building native SoA primitive meshes...");

        // ====================================================================
        // 1. SQUARE (UI Fast-Path, Bypasses Compute Shader, No Meshlet needed)
        // ====================================================================
        SQUARE = new Mesh("square");
        float[] sqPos = { -0.5f, -0.5f, 0f,   -0.5f, 0.5f, 0f,   0.5f, 0.5f, 0f,   0.5f, -0.5f, 0f };
        float[] sqUv  = { 0,0, 0,1, 1,1, 1,0 };
        int[] sqInd   = { 0, 1, 3, 3, 1, 2 };

        loader.GeomRegistry.appendPrimitive(SQUARE, sqPos, sqUv, sqInd);
        loader.MeshRegistry.register("square", SQUARE);


        // QUAD
        QUAD = new Mesh("quad");
        float[] qdPos = { -0.5f, -0.5f, 0f,   -0.5f, 0.5f, 0f,   0.5f, 0.5f, 0f,   0.5f, -0.5f, 0f };
        float[] qdUv  = { 0,0, 0,1, 1,1, 1,0 };
        int[] qdInd   = { 0, 1, 3, 3, 1, 2 };
        float[] qdNorm = { 0,1,0, 0,1,0, 0,1,0, 0,1,0 };
        float[] qdTang = new float[qdNorm.length];
        buildSingleMeshletPrimitive(QUAD, qdPos, qdUv, qdNorm, qdTang, qdInd, 0f, 0f, 0f, 0.707f, 0f, 1f, 0f, 0f);

        // ====================================================================
        // 2. CUBE (3D Entity, Requires 1 Native Meshlet for Culling)
        // ====================================================================
        CUBE = new Mesh("cube");
        float[] cPos = {
                -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
                0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
                -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f,
                -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f,
                0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f,
                -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f
        };
        float[] cUv = {
                1,1, 1,0, 0,0, 0,1, 1,1, 1,0, 0,0, 0,1, 1,1, 1,0, 0,0, 0,1,
                1,1, 1,0, 0,0, 0,1, 1,1, 1,0, 0,0, 0,1, 1,1, 1,0, 0,0, 0,1
        };
        float[] cNorm = {
                0,0,1, 0,0,1, 0,0,1, 0,0,1, 0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1,
                0,1,0, 0,1,0, 0,1,0, 0,1,0, 0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0,
                1,0,0, 1,0,0, 1,0,0, 1,0,0, -1,0,0, -1,0,0, -1,0,0, -1,0,0
        };
        int[] cInd = {
                0, 3, 1, 3, 2, 1, 4, 7, 5, 7, 6, 5, 8, 11, 9, 11, 10, 9,
                12, 15, 13, 15, 14, 13, 16, 19, 17, 19, 18, 17, 20, 23, 21, 23, 22, 21
        };
        float[] cTang = new float[cNorm.length]; // Blank tangents for unlit primitives

        // Bounding Sphere for a 1x1x1 cube is centered at 0,0,0 with a radius of ~0.866
        buildSingleMeshletPrimitive(CUBE, cPos, cUv, cNorm, cTang, cInd, 0f, 0f, 0f, 1.732f, 0f, 1f, 0f, 2f);


        // ====================================================================
        // 3. FLOOR (3D Entity, Requires 1 Native Meshlet for Culling)
        // ====================================================================
        FLOOR = new Mesh("floor");
        float[] fPos = { -0.5f, 0f, -0.5f,   -0.5f, 0f, 0.5f,   0.5f, 0f, 0.5f,   0.5f, 0f, -0.5f };
        float[] fUv = { 0,0, 0,200, 200,200, 200,0 };
        float[] fNorm = { 0,1,0, 0,1,0, 0,1,0, 0,1,0 };

        int[] fInd = { 0, 3, 1, 3, 2, 1 };
        float[] fTang = new float[fNorm.length];

        buildSingleMeshletPrimitive(FLOOR, fPos, fUv, fNorm, fTang, fInd, 0f, 0f, 0f, 0.707f, 0f, 1f, 0f, 2f);
    }

    /**
     * Instantly builds the required Data and Bounds structs to make a primitive
     * visible to the Compute Shader pipeline.
     */
    // ---> THE FIX: Add the 4 cone variables to the parameters <---
    private static void buildSingleMeshletPrimitive(Mesh mesh, float[] pos, float[] uvs, float[] norms, float[] tangs, int[] inds, float bX, float bY, float bZ, float bRadius, float cX, float cY, float cZ, float cCutoff) {

        int[] mData = new int[] { 0, inds.length, 0, 0 };
        float[] mBounds = new float[] { bX, bY, bZ, bRadius };

        // ---> THE FIX: Pack the cone data <---
        float[] mCone = new float[] { cX, cY, cZ, cCutoff };

        mesh.meshletCount = 1;

        // ---> THE FIX: Pass mCone to the Registry <---
        loader.GeomRegistry.appendMeshlet(mesh, pos, uvs, norms, tangs, inds, mData, mBounds, mCone);
        loader.MeshRegistry.register(mesh.name, mesh);
    }
}