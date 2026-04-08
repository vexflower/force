package renderer;

import org.lwjgl.system.MemoryUtil;

public class SceneSnapshot {
    public boolean isOffscreen = false;
    public int containerId = -1;
    public int width = 0;
    public int height = 0;
    public float bgR, bgG, bgB, bgA;
    public float[] vpMatrix = new float[16];

    // Allocate a raw off-heap block for this snapshot (supports up to 100k entities)
    public long entityDataPtr = MemoryUtil.nmemAlloc(100000 * 96L);

    public int globalEntityOffset = 0;
    public int entityCount = 0;

    public int[] groupFirstIndex = new int[4096];
    public int[] groupIndexCounts = new int[4096];
    public int[] groupInstanceCounts = new int[4096];
    public int groupCount = 0;

    public void clear() {
        entityCount = 0;
        groupCount = 0;
        isOffscreen = false;
        containerId = -1;
        globalEntityOffset = 0;
    }

    public void destroy() {
        MemoryUtil.nmemFree(entityDataPtr); // Prevent memory leaks on shutdown
    }
}