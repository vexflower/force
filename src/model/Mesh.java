package model;

import loader.MeshLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Data-Oriented Mesh Container.
 * Uses public fields for zero-overhead access.
 * Uses raw Data Streams instead of Java Serialization for lightning-fast load times.
 */
public class Mesh {
    public static final String MESH_DIRECTORY = System.getProperty("user.home") + File.separator + "framework" + File.separator + "meshes";

    public String name;
    public float[] positions;
    public float[] textures;
    public float[] normals;
    public float[] tangents;
    public int[] indices;

    // --- BINDLESS GEOMETRY TRACKERS ---
    public int vertexOffset; // Where in the Vertex Mega-Buffer does this start?
    public int firstIndex;   // Where in the Index Mega-Buffer does this start?
    public int indexCount;   // How many indices make up this specific mesh?

    // The Global ID assigned by the MeshLoader
    public int vaoId;

    // --- PRIMITIVE Singletons ---
    public static Mesh SQUARE;
    public static Mesh FLOOR;
    public static Mesh CUBE;

    // [CHANGED: 1] The static block ONLY defines the raw math data now.
    // It does NOT touch Vulkan or the MeshLoader.
    static {
        SQUARE = new Mesh("square");
        SQUARE.positions = new float[] {
                -0.5f, -0.5f, 0f, // Top Left      (Vulkan Y-Negative is UP)
                -0.5f,  0.5f, 0f, // Bottom Left   (Vulkan Y-Positive is DOWN)
                0.5f,  0.5f, 0f, // Bottom Right
                0.5f, -0.5f, 0f  // Top Right
        };
        // Standard UVs mapping to the upright canvas
        SQUARE.textures = new float[] {
                0,0,  // Top Left
                0,1,  // Bottom Left
                1,1,  // Bottom Right
                1,0   // Top Right
        };
        SQUARE.normals = new float[] {
                0,0,1,  0,0,1,  0,0,1,  0,0,1
        };
        // Counter-Clockwise winding order so it isn't culled
        SQUARE.indices = new int[] { 0, 1, 3,  3, 1, 2 };

        CUBE = new Mesh("cube");
        CUBE.positions = new float[] {
                // Front face
                -0.5f,  0.5f,  0.5f,  -0.5f, -0.5f,  0.5f,   0.5f, -0.5f,  0.5f,   0.5f,  0.5f,  0.5f,
                // Back face
                0.5f,  0.5f, -0.5f,   0.5f, -0.5f, -0.5f,  -0.5f, -0.5f, -0.5f,  -0.5f,  0.5f, -0.5f,
                // Top face
                -0.5f,  0.5f, -0.5f,  -0.5f,  0.5f,  0.5f,   0.5f,  0.5f,  0.5f,   0.5f,  0.5f, -0.5f,
                // Bottom face
                -0.5f, -0.5f,  0.5f,  -0.5f, -0.5f, -0.5f,   0.5f, -0.5f, -0.5f,   0.5f, -0.5f,  0.5f,
                // Right face
                0.5f,  0.5f,  0.5f,   0.5f, -0.5f,  0.5f,   0.5f, -0.5f, -0.5f,   0.5f,  0.5f, -0.5f,
                // Left face
                -0.5f,  0.5f, -0.5f,  -0.5f, -0.5f, -0.5f,  -0.5f, -0.5f,  0.5f,  -0.5f,  0.5f,  0.5f
        };
        // Flipped V-coordinate to fix the upside-down textures after righting the FBO canvas!
        CUBE.textures = new float[] {
                1,1,  1,0,  0,0,  0,1, // Front
                1,1,  1,0,  0,0,  0,1, // Back
                1,1,  1,0,  0,0,  0,1, // Top
                1,1,  1,0,  0,0,  0,1, // Bottom
                1,1,  1,0,  0,0,  0,1, // Right
                1,1,  1,0,  0,0,  0,1  // Left
        };
        // [NEW] Add the 24 normals for lighting calculations later
        CUBE.normals = new float[] {
                0,0,1, 0,0,1, 0,0,1, 0,0,1,       // Front
                0,0,-1, 0,0,-1, 0,0,-1, 0,0,-1,   // Back
                0,1,0, 0,1,0, 0,1,0, 0,1,0,       // Top
                0,-1,0, 0,-1,0, 0,-1,0, 0,-1,0,   // Bottom
                1,0,0, 1,0,0, 1,0,0, 1,0,0,       // Right
                -1,0,0, -1,0,0, -1,0,0, -1,0,0    // Left
        };
        // ---> THE FIX: Reversed the winding order so the outside faces point AT the camera!
        CUBE.indices = new int[] {
                0, 3, 1,   3, 2, 1,  // Front
                4, 7, 5,   7, 6, 5,  // Back
                8, 11, 9,  11, 10, 9, // Top
                12, 15, 13, 15, 14, 13, // Bottom
                16, 19, 17, 19, 18, 17, // Right
                20, 23, 21, 23, 22, 21  // Left
        };

        // --- NEW: THE TILING FLOOR ---
        FLOOR = new Mesh("floor");
        FLOOR.positions = new float[] {
                -0.5f, 0f, -0.5f, // Top Left
                -0.5f, 0f,  0.5f, // Bottom Left
                0.5f, 0f,  0.5f, // Bottom Right
                0.5f, 0f, -0.5f  // Top Right
        };
        // We set UVs to 200 so the texture repeats 200 times across the surface
        FLOOR.textures = new float[] {
                0,0,
                0,200,
                200,200,
                200,0
        };
        // Inside the static {} block where FLOOR is defined:
        FLOOR.normals = new float[] {
                0,1,0,  0,1,0,  0,1,0,  0,1,0
        };
        // [FIXED]: Reversed the winding order so it faces UP (+Y)
        FLOOR.indices = new int[] { 0, 3, 1,  3, 2, 1 };
    }

    public Mesh(String name) {
        this.name = name;
    }

    public void exportObject() {
        File directory = new File(MESH_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // We use .dat to signify raw binary data, not a Java .ser object
        File file = new File(directory, name + ".dat");

        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeUTF(name);
            writeFloatArray(out, positions);
            writeFloatArray(out, textures);
            writeFloatArray(out, normals);
            writeFloatArray(out, tangents);
            writeIntArray(out, indices);
        } catch (IOException e) {
            throw new RuntimeException("Failed to export binary mesh: " + name, e);
        }
    }

    // [CHANGED: 2] We created an explicit method that takes the commandPool.
    // We will call this manually from the MasterRenderer once Vulkan is alive.
    public static void initPrimitives() {
        System.out.println("Uploading primitive meshes to GPU...");
        MeshLoader.loadMesh(SQUARE);
        MeshLoader.loadMesh(CUBE);
        MeshLoader.loadMesh(FLOOR);
    }

    public static Mesh importObject(String fileName) {
        File file = new File(fileName);
        InputStream inStream;

        try {
            if (file.exists()) {
                inStream = new FileInputStream(file);
            } else {
                inStream = Mesh.class.getResourceAsStream("/io/meshes/" + fileName);
                if (inStream == null) throw new IOException("File not found in resources.");
            }

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(inStream))) {
                Mesh mesh = new Mesh(in.readUTF());
                mesh.positions = readFloatArray(in);
                mesh.textures = readFloatArray(in);
                mesh.normals = readFloatArray(in);
                mesh.tangents = readFloatArray(in);
                mesh.indices = readIntArray(in);
                return mesh;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to import binary mesh: " + fileName, e);
        }
    }

    // --- Helper methods to dump/read primitive arrays directly ---

    private void writeFloatArray(DataOutputStream out, float[] arr) throws IOException {
        if (arr == null) {
            out.writeInt(0);
            return;
        }
        out.writeInt(arr.length);
        for (float v : arr) out.writeFloat(v);
    }

    private static float[] readFloatArray(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == 0) return null;
        float[] arr = new float[length];
        for (int i = 0; i < length; i++) arr[i] = in.readFloat();
        return arr;
    }

    private void writeIntArray(DataOutputStream out, int[] arr) throws IOException {
        if (arr == null) {
            out.writeInt(0);
            return;
        }
        out.writeInt(arr.length);
        for (int v : arr) out.writeInt(v);
    }

    private static int[] readIntArray(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == 0) return null;
        int[] arr = new int[length];
        for (int i = 0; i < length; i++) arr[i] = in.readInt();
        return arr;
    }
}