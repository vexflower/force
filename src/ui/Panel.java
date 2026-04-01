package ui;

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

    // You can add Panel-specific properties here later, like:
    // public float backgroundColorR, G, B, A;
    // public int backgroundImageTextureId;
}