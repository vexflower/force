package mesh;

import loader.GeomRegistry;
import loader.MeshRegistry;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public final class MeshLoader {
    private static int meshCount = 0;
    private static final int MAGIC_NUMBER = 0x4D53484C;

    public static Mesh loadMeshlet(String path) {
        // Force the path to point to your physical Asset Vault
        String fullPath = resources.Resources.ENGINE_BASE_PATH + path;
        System.out.println("Streaming Binary MSHL: " + fullPath);
        String meshName = fullPath.substring(fullPath.lastIndexOf('\\') + 1);
        if (meshName.contains(".")) meshName = meshName.substring(0, meshName.lastIndexOf('.'));

        File file = new File(fullPath);
        if (!file.exists()) throw new RuntimeException("Missing .meshl file: " + path);

        try (FileInputStream fis = new FileInputStream(file); FileChannel channel = fis.getChannel()) {

            // 1. Read the 16-byte Header
            ByteBuffer header = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(header);
            header.flip();

            if (header.getInt() != MAGIC_NUMBER) throw new RuntimeException("Invalid .meshl format!");
            int vertCount = header.getInt();
            int indCount = header.getInt();
            int meshletCount = header.getInt();

            // 2. Allocate the exact size for the body
            int bodySize = (vertCount * 3 * 4) + (vertCount * 2 * 4) + (vertCount * 3 * 4) + (vertCount * 3 * 4) + (indCount * 4) + (meshletCount * 48);
            ByteBuffer body = ByteBuffer.allocateDirect(bodySize).order(ByteOrder.LITTLE_ENDIAN);
            channel.read(body);
            body.flip();

            // 3. Extract Arrays
            float[] positions = new float[vertCount * 3]; body.asFloatBuffer().get(positions); body.position(body.position() + positions.length * 4);
            float[] uvs = new float[vertCount * 2];       body.asFloatBuffer().get(uvs);       body.position(body.position() + uvs.length * 4);
            float[] normals = new float[vertCount * 3];   body.asFloatBuffer().get(normals);   body.position(body.position() + normals.length * 4);
            float[] tangents = new float[vertCount * 3];  body.asFloatBuffer().get(tangents);  body.position(body.position() + tangents.length * 4);
            int[] indices = new int[indCount];            body.asIntBuffer().get(indices);     body.position(body.position() + indices.length * 4);

            // 4. Extract Meshlets
            int[] meshletData = new int[meshletCount * 4];
            float[] meshletBounds = new float[meshletCount * 4];
            float[] meshletCones = new float[meshletCount * 4];
            for (int i = 0; i < meshletCount; i++) {
                meshletData[i*4]   = body.getInt(); // indexOffset
                meshletData[i*4+1] = body.getInt(); // indexCount
                meshletData[i*4+2] = body.getInt(); // vertexOffset (placeholder)
                meshletData[i*4+3] = body.getInt(); // padding

                meshletBounds[i*4]   = body.getFloat(); // bX
                meshletBounds[i*4+1] = body.getFloat(); // bY
                meshletBounds[i*4+2] = body.getFloat(); // bZ
                meshletBounds[i*4+3] = body.getFloat(); // bRadius

                meshletCones[i*4]   = body.getFloat();
                meshletCones[i*4+1] = body.getFloat();
                meshletCones[i*4+2] = body.getFloat();
                meshletCones[i*4+3] = body.getFloat();
            }

            // 5. Send directly to the SoA Registry!
            Mesh mesh = new Mesh(meshName);
            mesh.meshletCount = meshletCount;

            GeomRegistry.appendMeshlet(mesh, positions, uvs, normals, tangents, indices, meshletData, meshletBounds, meshletCones);
            MeshRegistry.register(meshName, mesh);

            System.out.println("   -> Loaded " + meshletCount + " meshlets in ~0.001s!");
            return mesh;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load .meshl: " + e.getMessage(), e);
        }
    }
}