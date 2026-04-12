package renderer;
import util.CFloatList;
import util.CIntList;

public class RenderState {
    public final int frameIndex;
    public int snapshotCount = 0;
    public SceneSnapshot[] snapshots = new SceneSnapshot[10];

    public int uiElementCount = 0;
    public final CFloatList uiTransforms = new CFloatList(1600);
    public final CIntList uiTextureIds = new CIntList(100);

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
        totalEntities = 0;
        uiTransforms.clear();
        uiTextureIds.clear();
        for (int i = 0; i < snapshots.length; i++) snapshots[i].clear();
    }

    public void free() {
        uiTransforms.free();
        uiTextureIds.free();
        for (int i = 0; i < snapshots.length; i++) snapshots[i].free();
    }
}


