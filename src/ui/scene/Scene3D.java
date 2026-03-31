package ui.scene;

import lang.Mat4;

public class Scene3D extends Scene {

    protected Mat4 projectionMatrix = new Mat4();
    protected Mat4 viewMatrix = new Mat4();
    private final Mat4 vpMatrix = new Mat4(); // Cached View * Projection

    public Scene3D(int width, int height) {
        super(width, height);
    }

    @Override
    public void init() {
        onResize(this.width, this.height);
        viewMatrix.identity().translate(0, 0, -2.0f);
    }

    @Override
    protected void onResize(int width, int height) {
        if (height == 0) height = 1;
        float aspect = (float) width / (float) height;
        // Instantly rebuilds the 3D lens to match its physical panel size
        projectionMatrix.perspective(70f, aspect, 0.1f, 1000f);
    }

    @Override
    public void update(float delta) {
        // 1. Combine View and Projection once per frame
        projectionMatrix.mul(viewMatrix, vpMatrix);

        // 2. Tick all entities and let them multiply their Model against the cached VP
        for (int i = 0; i < activeEntities.size(); i++) {
            activeEntities.get(i).update(delta, vpMatrix);
        }
    }
}