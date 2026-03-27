package model;

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