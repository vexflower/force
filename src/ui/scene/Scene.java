package ui.scene;

import environment.RendererManager;
import lang.Mat4;
import renderer.RenderState;
import ui.Container;
import util.IntList;
import hardware.Display; // [NEW] Needed to poll the screen size

public abstract class Scene extends Container {

    public IntList activeEntities = new IntList();
    private final Mat4 orthoProjection = new Mat4();

    // [NEW] Track screen dimensions to detect resizes
    private int currentWidth;
    private int currentHeight;

    public Scene() {
        super();
    }

    public Scene(int width, int height) {
        super(width, height);
        this.currentWidth = width;
        this.currentHeight = height;
        orthoProjection.ortho(0, width, height, 0, -1f, 1f);
    }

    public abstract void init(long commandPool);
    public abstract void update(float delta);

    // [NEW] Hook for subclasses (like Scene3D) to update their Perspective matrices
    protected void onResize(int width, int height) {}

    /**
     * Engine-level tick called by GameEngine.java
     */
    public void engineUpdate(float delta) {
        // 1. Detect Window Resizes instantly (Zero-Allocation poll)
        int dw = Display.getWidth();
        int dh = Display.getHeight();
        boolean screenResized = false;

        if (dw != currentWidth || dh != currentHeight) {
            currentWidth = dw;
            currentHeight = dh;
            // Rebuild the 2D pixel-perfect projection
            orthoProjection.ortho(0, dw, dh, 0, -1f, 1f);

            // Notify the 3D scene to fix its aspect ratio
            onResize(dw, dh);
            screenResized = true;
        }

        // 2. Run standard game logic
        update(delta);

        // 3. Run UI logic and animations
        super.update(delta);

        // 4. Perform the Zero-GC Matrix cascade!
        // [THE FIX]: If the screen resized, we pass 'true' to force all UI elements
        // to multiply against the new Ortho Matrix and fix their physical screen positions!
        this.updateTransform(orthoProjection, screenResized);
    }

    public int addEntity() {
        int id = RendererManager.createEntity();
        activeEntities.add(id);
        return id;
    }

    public void extractUIData(RenderState state) {
        state.uiElementCount = 0;
        state.fboUpdateCount = 0;
        extractContainer(this, state);
    }

    private void extractContainer(Container container, RenderState state) {
        if (container.textureId != -1 && state.uiElementCount < 100) {
            int index = state.uiElementCount++;
            state.uiTextureIds[index] = container.textureId;
            container.absoluteTransform.store(state.uiTransforms, index * 16);

            if (state.fboUpdateCount < state.fboQueue.length) {
                state.fboQueue[state.fboUpdateCount++] = container;
            }
        }

        for (int i = 0; i < container.children.size(); i++) {
            extractContainer(container.children.get(i), state);
        }
    }
}