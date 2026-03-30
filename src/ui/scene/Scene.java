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

    // [NEW] The Root Swing Panel!
    private Container contentPane;

    // [NEW] The pure origin point to start the UI math
    private static final Mat4 ROOT_PIXEL_MATRIX = new Mat4();

    public Scene() {
        super();
    }

    public Scene(int width, int height) {
        super(width, height);
        this.currentWidth = width;
        this.currentHeight = height;
        orthoProjection.ortho(0, width, height, 0, -1f, 1f);
    }

    public void setContentPane(Container panel) {
        this.children.clear(); // Remove old pane
        this.contentPane = panel;
        this.add(panel);
        this.isDirty = true;
    }

    public abstract void init(long commandPool);
    public abstract void update(float delta);

    // [NEW] Hook for subclasses (like Scene3D) to update their Perspective matrices
    protected void onResize(int width, int height) {}

    /**
     * Engine-level tick called by GameEngine.java
     */
    public void engineUpdate(float delta) {
        int dw = Display.getWidth();
        int dh = Display.getHeight();
        boolean screenResized = false;

        if (dw != currentWidth || dh != currentHeight) {
            currentWidth = dw;
            currentHeight = dh;

            // [THE SWING FIX]: Force the Root pane to exactly match the screen size!
            if (contentPane != null) {
                contentPane.setSize(dw, dh);
            }

            onResize(dw, dh);
            screenResized = true;
        }

        update(delta);
        super.update(delta);

        // [THE FIX]: Pass the pure Identity Matrix! No more double-projection!
        this.updateTransform(ROOT_PIXEL_MATRIX, screenResized);
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

            // [THE FIX]: We push the Render Transform to the GPU, NOT the absolute!
            container.renderTransform.store(state.uiTransforms, index * 16);

            if (state.fboUpdateCount < state.fboQueue.length) {
                state.fboQueue[state.fboUpdateCount++] = container;
            }
        }

        for (int i = 0; i < container.children.size(); i++) {
            extractContainer(container.children.get(i), state);
        }
    }
}