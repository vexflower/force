package environment;

import util.IntList;

public abstract class Scene {
    // Tracks which entities exist in THIS specific scene
    public IntList activeEntities = new IntList();

    public Scene() {}

    // Initialize the scene (load textures, spawn entities)
    public abstract void init(long commandPool);

    // Update game logic (runs 60 times a second)
    public abstract void update(float delta);

    public int addEntity() {
        int id = RendererManager.createEntity();
        activeEntities.add(id);
        return id;
    }

    public void removeEntity(int id) {
        // We will implement fast removals later, but the structure is here!
    }
}