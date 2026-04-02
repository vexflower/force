package ui;

import renderer.RenderState;

/**
 * The standard stackable UI element.
 * Backed by an autonomous Vulkan FrameBufferObject and registered in the Bindless Phonebook.
 */
public class Panel extends Container {

    public Panel() {
        super();
    }

    public Panel(int width, int height) {
        super(width, height);
    }

    @Override
    public void extractUIData(RenderState state) {
        // Init FBO just-in-time
        if (this.textureId == -1 && this.width > 0 && this.height > 0) {
            this.init();
        }
        super.extractUIData(state);
    }

    @Override
    public void extract3DEntities(renderer.RenderState state) {
        // Force a snapshot so the MasterRenderer binds and clears this FBO!
        if (this.textureId != -1 && state.snapshotCount < state.snapshots.length) {
            renderer.SceneSnapshot snap = state.snapshots[state.snapshotCount++];
            snap.fboReference = this;
            snap.entityCount = 0; // It's just a UI panel, no 3D entities
        }
        super.extract3DEntities(state);
    }
}