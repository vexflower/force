package renderer;

import ui.FrameBufferObject;

public class RenderState {
    // --- 3D ENTITY DATA ---
    public int entityCount = 0;
    public float[] transforms = new float[1600];
    public int[] meshIds = new int[100];
    public int[] textureIds = new int[100];

    // --- 2D UI & FBO DATA ---
    public int uiElementCount = 0;
    public float[] uiTransforms = new float[1600]; // 100 UI elements * 16 floats
    public int[] uiTextureIds = new int[100];      // The Bindless FBO Texture IDs!

    // The FBO Redraw Queue (Tells the Render thread which FBOs need to update their textures this frame)
    public int fboUpdateCount = 0;
    // We pass the object reference directly to access the pre-allocated Vulkan structs
    public FrameBufferObject[] fboQueue = new FrameBufferObject[50];
}