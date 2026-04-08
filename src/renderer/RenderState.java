package renderer;

public class RenderState {
    public final int frameIndex;
    public int snapshotCount = 0;
    public SceneSnapshot[] snapshots = new SceneSnapshot[10];

    public int uiElementCount = 0;
    public float[] uiTransforms = new float[1600];
    public int[] uiTextureIds = new int[100];

    // ---> NEW: Track total entities across ALL scenes to prevent memory overlap
    public int totalEntities = 0;

    public RenderState(int frameIndex) {
        this.frameIndex = frameIndex;
        for (int i = 0; i < snapshots.length; i++) {
            snapshots[i] = new SceneSnapshot();
        }
    }

    public void clear() {
        snapshotCount = 0;
        uiElementCount = 0;
        totalEntities = 0; // <--- Clear it!
        for (int i = 0; i < snapshots.length; i++) snapshots[i].clear();
    }
}