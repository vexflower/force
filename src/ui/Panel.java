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
        // By calling super(width, height), this Panel is now ready
        // to have init() called on it to generate its Vulkan FBO.
    }

    // You can add Panel-specific properties here later, like:
    // public float backgroundColorR, G, B, A;
    // public int backgroundImageTextureId;
}