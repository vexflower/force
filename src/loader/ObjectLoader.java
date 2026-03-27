package loader;

import model.Mesh;
import util.FloatList;
import util.IntList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * AAA-Grade OBJ Parser.
 * Uses zero-allocation byte-level parsing to bypass 'String.split' and regex entirely.
 * Calculates Tangents automatically for Normal Mapping.
 */
public class ObjectLoader {

    public static void loadAllObjects() {
        File meshDir = new File(System.getProperty("user.home") + File.separator + "framework" + File.separator + "objects");
        if (!meshDir.exists()) {
            System.out.println("Mesh directory does not exist.");
            return;
        }

        File[] files = meshDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".obj"));
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();
            String meshName = fileName.substring(0, fileName.lastIndexOf('.'));

            try {
                Mesh mesh = parseObjFromFile(file.getAbsolutePath(), meshName);
                mesh.exportObject();
                System.out.println("Processed and exported: " + meshName);
            } catch (IOException e) {
                System.err.println("Failed to load: " + fileName);
            }
        }
    }

    public static Mesh parseObjFromFile(String filePath, String meshName) throws IOException {
        // Read the entire file instantly into RAM as raw ASCII bytes
        byte[] data = Files.readAllBytes(Paths.get(filePath));

        // Temporary data lists (The file order)
        FloatList rawPos = new FloatList();
        FloatList rawTex = new FloatList();
        FloatList rawNorm = new FloatList();

        // Final flattened arrays for OpenGL VBOs
        FloatList outPos = new FloatList();
        FloatList outTex = new FloatList();
        FloatList outNorm = new FloatList();
        IntList outIndices = new IntList();

        int[] offset = new int[]{0}; // Pointer to track our position in the byte array

        while (offset[0] < data.length) {
            int i = offset[0];

            while (i < data.length && (data[i] == ' ' || data[i] == '\t' || data[i] == '\r' || data[i] == '\n')) i++;
            if (i >= data.length) break;

            if (data[i] == 'v' && data[i + 1] == ' ') {
                offset[0] = i + 2;
                rawPos.add(parseFloat(data, offset), parseFloat(data, offset), parseFloat(data, offset));
            } else if (data[i] == 'v' && data[i + 1] == 't' && data[i + 2] == ' ') {
                offset[0] = i + 3;
                rawTex.add(parseFloat(data, offset), parseFloat(data, offset));
            } else if (data[i] == 'v' && data[i + 1] == 'n' && data[i + 2] == ' ') {
                offset[0] = i + 3;
                rawNorm.add(parseFloat(data, offset), parseFloat(data, offset), parseFloat(data, offset));
            } else if (data[i] == 'f' && data[i + 1] == ' ') {
                offset[0] = i + 2;
                parseFace(data, offset, rawPos, rawTex, rawNorm, outPos, outTex, outNorm, outIndices);
            } else {
                while (i < data.length && data[i] != '\n') i++;
                offset[0] = i;
            }
        }

        Mesh mesh = new Mesh(meshName);
        mesh.positions = outPos.toArray();
        mesh.textures = outTex.toArray();
        mesh.normals = outNorm.toArray();
        mesh.indices = outIndices.toArray();

        // Automatically calculate Tangents for the shader (Zero-Allocation mathematics)
        calculateTangents(mesh);

        System.out.println("Parsed OBJ file: " + filePath);
        System.out.println("Final Output - Vertices: " + mesh.positions.length / 3 + ", Indices: " + mesh.indices.length);

        return mesh;
    }

    /**
     * Mathematically deduces the Tangent vectors by comparing the physical edges of the triangle
     * against the UV coordinate mapping. Essential for Normal Mapping.
     */
    private static void calculateTangents(Mesh mesh) {
        float[] tangents = new float[mesh.positions.length];

        // Process one triangle (3 indices) at a time
        for (int i = 0; i < mesh.indices.length; i += 3) {
            int i0 = mesh.indices[i];
            int i1 = mesh.indices[i + 1];
            int i2 = mesh.indices[i + 2];

            // Get XYZ positions
            float v0x = mesh.positions[i0 * 3], v0y = mesh.positions[i0 * 3 + 1], v0z = mesh.positions[i0 * 3 + 2];
            float v1x = mesh.positions[i1 * 3], v1y = mesh.positions[i1 * 3 + 1], v1z = mesh.positions[i1 * 3 + 2];
            float v2x = mesh.positions[i2 * 3], v2y = mesh.positions[i2 * 3 + 1], v2z = mesh.positions[i2 * 3 + 2];

            // Get UV coordinates
            float uv0x = mesh.textures[i0 * 2], uv0y = mesh.textures[i0 * 2 + 1];
            float uv1x = mesh.textures[i1 * 2], uv1y = mesh.textures[i1 * 2 + 1];
            float uv2x = mesh.textures[i2 * 2], uv2y = mesh.textures[i2 * 2 + 1];

            // Calculate physical edges
            float deltaPos1x = v1x - v0x, deltaPos1y = v1y - v0y, deltaPos1z = v1z - v0z;
            float deltaPos2x = v2x - v0x, deltaPos2y = v2y - v0y, deltaPos2z = v2z - v0z;

            // Calculate UV edges
            float deltaUV1x = uv1x - uv0x, deltaUV1y = uv1y - uv0y;
            float deltaUV2x = uv2x - uv0x, deltaUV2y = uv2y - uv0y;

            // Calculate the fraction. Protect against division by zero if UVs are bad/missing.
            float r = 1.0f / (deltaUV1x * deltaUV2y - deltaUV1y * deltaUV2x);
            if (Float.isInfinite(r) || Float.isNaN(r)) r = 0.0f;

            // Cross multiply
            float tx = (deltaPos1x * deltaUV2y - deltaPos2x * deltaUV1y) * r;
            float ty = (deltaPos1y * deltaUV2y - deltaPos2y * deltaUV1y) * r;
            float tz = (deltaPos1z * deltaUV2y - deltaPos2z * deltaUV1y) * r;

            // Accumulate the tangent vectors back onto the vertices
            tangents[i0 * 3] += tx; tangents[i0 * 3 + 1] += ty; tangents[i0 * 3 + 2] += tz;
            tangents[i1 * 3] += tx; tangents[i1 * 3 + 1] += ty; tangents[i1 * 3 + 2] += tz;
            tangents[i2 * 3] += tx; tangents[i2 * 3 + 1] += ty; tangents[i2 * 3 + 2] += tz;
        }

        // Normalize all tangents at the end so they have a length of exactly 1.0
        for (int i = 0; i < tangents.length; i += 3) {
            float tx = tangents[i], ty = tangents[i + 1], tz = tangents[i + 2];
            float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len > 0) {
                tangents[i] = tx / len;
                tangents[i + 1] = ty / len;
                tangents[i + 2] = tz / len;
            }
        }

        mesh.tangents = tangents;
    }

    private static void parseFace(byte[] data, int[] offset,
                                  FloatList rawPos, FloatList rawTex, FloatList rawNorm,
                                  FloatList outPos, FloatList outTex, FloatList outNorm, IntList outIndices) {
        int startVertexCount = outPos.size() / 3;
        int firstVertexIndex = -1;
        int previousVertexIndex = -1;
        int vertexCount = 0;

        int i = offset[0];
        while (i < data.length && data[i] != '\n' && data[i] != '\r') {
            while (i < data.length && (data[i] == ' ' || data[i] == '\t')) i++;
            if (i >= data.length || data[i] == '\n' || data[i] == '\r') break;

            offset[0] = i;
            int posIdx = parseInt(data, offset) - 1;
            int texIdx = -1;
            int normIdx = -1;

            i = offset[0];
            if (i < data.length && data[i] == '/') {
                i++;
                if (i < data.length && data[i] != '/') {
                    offset[0] = i;
                    texIdx = parseInt(data, offset) - 1;
                    i = offset[0];
                }
                if (i < data.length && data[i] == '/') {
                    i++;
                    offset[0] = i;
                    normIdx = parseInt(data, offset) - 1;
                    i = offset[0];
                }
            }

            outPos.add(rawPos.get(posIdx * 3), rawPos.get(posIdx * 3 + 1), rawPos.get(posIdx * 3 + 2));

            if (texIdx >= 0) outTex.add(rawTex.get(texIdx * 2), rawTex.get(texIdx * 2 + 1));
            else outTex.add(0f, 0f);

            if (normIdx >= 0) outNorm.add(rawNorm.get(normIdx * 3), rawNorm.get(normIdx * 3 + 1), rawNorm.get(normIdx * 3 + 2));
            else outNorm.add(0f, 1f, 0f);

            int currentVertexIndex = startVertexCount + vertexCount;
            vertexCount++;

            if (vertexCount >= 3) {
                outIndices.add(firstVertexIndex);
                outIndices.add(previousVertexIndex);
                outIndices.add(currentVertexIndex);
            } else if (vertexCount == 1) {
                firstVertexIndex = currentVertexIndex;
            }
            previousVertexIndex = currentVertexIndex;
        }
        offset[0] = i;
    }

    /**
     * Extracts a floating-point number directly from an ASCII byte array.
     * Insanely fast. Bypasses the heavy Float.parseFloat() logic.
     */
    private static float parseFloat(byte[] data, int[] offset) {
        int i = offset[0];
        while (i < data.length && (data[i] == ' ' || data[i] == '\t')) i++;

        boolean negative = false;
        if (i < data.length && data[i] == '-') { negative = true; i++; }
        else if (i < data.length && data[i] == '+') { i++; }

        float value = 0;
        while (i < data.length && data[i] >= '0' && data[i] <= '9') {
            value = value * 10 + (data[i] - '0');
            i++;
        }

        if (i < data.length && data[i] == '.') {
            i++;
            float fraction = 0;
            float divisor = 1;
            while (i < data.length && data[i] >= '0' && data[i] <= '9') {
                fraction = fraction * 10 + (data[i] - '0');
                divisor *= 10;
                i++;
            }
            value += fraction / divisor;
        }

        // Handle Scientific Notation (E/e) exported by some 3D software
        if (i < data.length && (data[i] == 'e' || data[i] == 'E')) {
            i++;
            boolean expNegative = false;
            if (i < data.length && data[i] == '-') { expNegative = true; i++; }
            else if (i < data.length && data[i] == '+') { i++; }

            int exp = 0;
            while (i < data.length && data[i] >= '0' && data[i] <= '9') {
                exp = exp * 10 + (data[i] - '0');
                i++;
            }
            value = (float) (value * Math.pow(10, expNegative ? -exp : exp));
        }

        offset[0] = i;
        return negative ? -value : value;
    }

    /**
     * Extracts an integer directly from an ASCII byte array.
     */
    private static int parseInt(byte[] data, int[] offset) {
        int i = offset[0];
        while (i < data.length && (data[i] == ' ' || data[i] == '\t')) i++;

        boolean negative = false;
        if (i < data.length && data[i] == '-') { negative = true; i++; }
        else if (i < data.length && data[i] == '+') { i++; }

        int value = 0;
        while (i < data.length && data[i] >= '0' && data[i] <= '9') {
            value = value * 10 + (data[i] - '0');
            i++;
        }
        offset[0] = i;
        return negative ? -value : value;
    }
}