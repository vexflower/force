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

    // The Global ID assigned by the MeshLoader
    public int vaoId;

    // --- PRIMITIVE Singletons ---
    public static Mesh SQUARE;
    public static Mesh CUBE;

    // [CHANGED: 1] The static block ONLY defines the raw math data now.
    // It does NOT touch Vulkan or the MeshLoader.
    static {
        SQUARE = new Mesh("Square");
        SQUARE.positions = new float[] {
                -0.5f,  0.5f, 0f, // Top Left
                -0.5f, -0.5f, 0f, // Bottom Left
                0.5f, -0.5f, 0f, // Bottom Right
                0.5f,  0.5f, 0f  // Top Right
        };
        SQUARE.textures = new float[] { 0,0,  0,1,  1,1,  1,0 };
        SQUARE.indices = new int[] { 0,1,3,  3,1,2 };

        // Add this right below the SQUARE definition inside the static block of Mesh.java

        CUBE = new Mesh("Cube");

        // 24 Vertices (4 per face)
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

        // Standard 0.0 to 1.0 UV mapping for every face
        CUBE.textures = new float[] {
                // Front
                0,0,  0,1,  1,1,  1,0,
                // Back
                0,0,  0,1,  1,1,  1,0,
                // Top
                0,0,  0,1,  1,1,  1,0,
                // Bottom
                0,0,  0,1,  1,1,  1,0,
                // Right
                0,0,  0,1,  1,1,  1,0,
                // Left
                0,0,  0,1,  1,1,  1,0
        };

        // 36 Indices (6 faces * 2 triangles * 3 vertices)
        CUBE.indices = new int[] {
                0, 1, 3,  3, 1, 2, // Front
                4, 5, 7,  7, 5, 6, // Back
                8, 9,11, 11, 9,10, // Top
                12,13,15, 15,13,14, // Bottom
                16,17,19, 19,17,18, // Right
                20,21,23, 23,21,22  // Left
        };

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