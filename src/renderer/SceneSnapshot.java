package renderer;

import ui.FrameBufferObject;

public class SceneSnapshot {
    // If this is NULL, it means this snapshot belongs to the Main Window.
    // If it has a reference, it means this snapshot is an off-screen FBO.
    public FrameBufferObject fboReference = null;

    public int entityCount = 0;
    public float[] transforms = new float[1600]; // Enough for 100 entities per scene
    public int[] meshIds = new int[100];
    public int[] textureIds = new int[100];

    public void clear() {
        entityCount = 0;
        fboReference = null;
    }
}