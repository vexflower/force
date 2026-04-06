package loader;

import model.Mesh;
import java.util.HashMap;
import java.util.Map;

public class MeshRegistry {
    private static final Map<String, Mesh> meshes = new HashMap<>();
    private static final Mesh[] meshById = new Mesh[4096]; // Fast O(1) integer lookup

    public static void register(String name, Mesh mesh) {
        meshes.put(name, mesh);
        meshById[mesh.vaoId] = mesh; // Save it by ID too!
    }

    public static Mesh get(String name) {
        Mesh mesh = meshes.get(name);
        if (mesh == null) throw new RuntimeException("Mesh not found in Registry: " + name);
        return mesh;
    }

    // NEW: Allow the renderer to fetch meshes instantly by ID
    public static Mesh get(int id) {
        return meshById[id];
    }
}