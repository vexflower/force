package loader;

import hardware.Display;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Tracks raw Vulkan memory handles for textures and maps them to a simple Integer ID.
 */
public class TextureRegistry {

    // Store the 64-bit Vulkan pointers
    private static long[] images = new long[1024];
    private static long[] memories = new long[1024];
    private static long[] views = new long[1024];
    private static long[] samplers = new long[1024];

    private static int count = 0;

    public static int add(long image, long memory, long view, long sampler) {
        if (count == images.length) {
            expand();
        }

        int id = count;
        images[id] = image;
        memories[id] = memory;
        views[id] = view;
        samplers[id] = sampler;

        count++;
        return id;
    }

    private static void expand() {
        int newSize = images.length * 2;

        long[] newImages = new long[newSize];
        System.arraycopy(images, 0, newImages, 0, count);
        images = newImages;

        long[] newMemories = new long[newSize];
        System.arraycopy(memories, 0, newMemories, 0, count);
        memories = newMemories;

        long[] newViews = new long[newSize];
        System.arraycopy(views, 0, newViews, 0, count);
        views = newViews;

        long[] newSamplers = new long[newSize];
        System.arraycopy(samplers, 0, newSamplers, 0, count);
        samplers = newSamplers;
    }

    /**
     * Called during engine shutdown to prevent memory leaks.
     */
    public static void destroy() {
        for (int i = 0; i < count; i++) {
            vkDestroySampler(Display.getDevice(), samplers[i], null);
            vkDestroyImageView(Display.getDevice(), views[i], null);
            vkDestroyImage(Display.getDevice(), images[i], null);
            vkFreeMemory(Display.getDevice(), memories[i], null);
        }
        count = 0;
    }
}