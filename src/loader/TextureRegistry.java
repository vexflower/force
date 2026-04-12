package loader;

import hardware.VulkanContext;
import org.lwjgl.vulkan.VkDevice;
import util.CLongList;
import static org.lwjgl.vulkan.VK10.*;

public class TextureRegistry {
    // Pure C-Memory Trackers
    private static final CLongList images = new CLongList(1024);
    private static final CLongList memories = new CLongList(1024);
    private static final CLongList views = new CLongList(1024);
    private static final CLongList samplers = new CLongList(1024);

    public static int add(long image, long memory, long view, long sampler) {
        int id = images.size();
        images.add(image);
        memories.add(memory);
        views.add(view);
        samplers.add(sampler);
        return id;
    }

    public static void destroy() {
        VkDevice device = VulkanContext.getDevice();
        for (int i = 0; i < images.size(); i++) {
            vkDestroySampler(device, samplers.get(i), null);
            vkDestroyImageView(device, views.get(i), null);
            vkDestroyImage(device, images.get(i), null);
            vkFreeMemory(device, memories.get(i), null);
        }
        images.free(); memories.free(); views.free(); samplers.free();
    }
}