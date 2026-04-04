package renderer;

public class SceneSnapshot {
    public boolean isOffscreen = false;
    public int containerId = -1;
    public int width = 0;
    public int height = 0;
    public float bgR, bgG, bgB, bgA;

    // --- THE 100K MEGA-BUFFER ---
    public int entityCount = 0;
    // 24 floats per entity. 100,000 entities = 2,400,000 floats (~9.6 MB)
    public float[] entityData = new float[2_400_000];

    // Track the groups so we can issue batched instanced draw calls
    public int[] groupFirstIndex = new int[4096];
    public int[] groupIndexCounts = new int[4096];
    public int[] groupInstanceCounts = new int[4096];
    public int groupCount = 0;

    public void clear() {
        entityCount = 0;
        groupCount = 0;
        isOffscreen = false;
        containerId = -1;
    }
}