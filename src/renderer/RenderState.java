package renderer;

public class RenderState {
    // --- SCENE SNAPSHOTS (Zero GC Pool) ---
    public int snapshotCount = 0;
    // We pre-allocate enough memory to render 10 simultaneous scenes/FBOs per frame
    public SceneSnapshot[] snapshots = new SceneSnapshot[10];

    // --- 2D UI DATA ---
    public int uiElementCount = 0;
    public float[] uiTransforms = new float[1600];
    public int[] uiTextureIds = new int[100];

    public RenderState() {
        for (int i = 0; i < snapshots.length; i++) {
            snapshots[i] = new SceneSnapshot();
        }
    }

    public void clear() {
        snapshotCount = 0;
        uiElementCount = 0;
        for (int i = 0; i < snapshots.length; i++) {
            snapshots[i].clear();
        }
    }
}