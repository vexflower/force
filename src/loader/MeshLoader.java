package loader;

import model.Mesh;
import org.lwjgl.vulkan.*;

// [CHANGED: 1] Removed ALL org.lwjgl.opengl.* imports. Replaced with Vulkan 1.0


/**
 * High-performance Vulkan MeshLoader.
 * Replaces OpenGL VAOs/VBOs with Vulkan VkBuffers and Staging memory.
 */
public final class MeshLoader {

    private static int meshCount = 0;

    /**
     * Replaces the old Vulkan VAO/VBO logic.
     * Simply dumps the parsed mesh into the Global Registry.
     */
    public static void loadMesh(Mesh mesh) {
        GeomRegistry.appendMesh(mesh);

        // We still assign an ID for backwards compatibility with your EntityManager
        mesh.vaoId = meshCount++;
    }

    public static void destroy() {
        // Nothing to do here anymore! GlobalGeometryRegistry handles cleanup.
        meshCount = 0;
    }

}