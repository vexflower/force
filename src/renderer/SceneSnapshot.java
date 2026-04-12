package renderer;
import util.CFloatList;
import util.CIntList;

public class SceneSnapshot {
    public boolean isOffscreen = false;
    public int containerId = -1;
    public int width = 0, height = 0;
    public float bgR, bgG, bgB, bgA;

    public final CFloatList vpMatrix = new CFloatList(16);
    public final CFloatList entityData = new CFloatList(2_400_000);
    public final CIntList indirectData = new CIntList(50000);

    public int globalEntityOffset = 0;
    public int entityCount = 0;
    public int commandCount = 0;

    public void clear() {
        entityCount = 0;
        commandCount = 0;
        isOffscreen = false;
        containerId = -1;
        vpMatrix.clear();
        entityData.clear();
        indirectData.clear();
    }

    public void free() {
        vpMatrix.free(); entityData.free(); indirectData.free();
    }
}