package renderer;

public class SceneSnapshot {

    // The GPU instructions
    public boolean isOffscreen = false;
    public int containerId = -1;
    public int width = 0;
    public int height = 0;
    public float bgR, bgG, bgB, bgA;

    // The 3D Entities
    public int entityCount = 0;
    public float[] transforms = new float[160_000]; // 10000 entities
    public int[] meshIds = new int[4096];
    public int[] textureIds = new int[4096];

    public void clear() {
        entityCount = 0;
        isOffscreen = false;
        containerId = -1;
    }
}