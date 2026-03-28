package renderer;

public class RenderState {
    public int entityCount = 0;

    // Max 100 entities for now to keep memory pre-allocated and Zero-GC!
    public float[] transforms = new float[1600]; // 100 entities * 16 floats
    public int[] meshIds = new int[100];
    public int[] textureIds = new int[100];
}