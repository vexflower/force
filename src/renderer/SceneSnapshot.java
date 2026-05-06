package renderer;
import util.CFloatList;

public class SceneSnapshot {
    public boolean isOffscreen = false;
    public int containerId = -1;
    public int width = 0, height = 0;
    public float bgR, bgG, bgB, bgA;

    public final CFloatList vpMatrix = new CFloatList(16);
    public final CFloatList entityData = new CFloatList(2_400_000);

    public int globalEntityOffset = 0;
    public int entityCount = 0;

    // NEW: Store the physical camera location
    public float camX, camY, camZ;
    public float p11; // NEW: The Lens Projection Scaling Factor

    public void clear() {
        entityCount = 0;
        isOffscreen = false;
        containerId = -1;
        vpMatrix.clear();
        entityData.clear();
    }

    public void free() {
        vpMatrix.free();
        entityData.free();
    }
}