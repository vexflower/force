package loader;

import hardware.Display;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import util.VulkanUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;
import static org.lwjgl.stb.STBImage.stbi_set_flip_vertically_on_load;
import static org.lwjgl.vulkan.VK12.*;
import static resources.Resources.streamToOffHeap;

public class TextureLoader {

    // [CHANGED: 1] We must pass the commandPool so TextureLoader can create the Staging Buffer
    public static int loadTexture(String path, long commandPool) {
        System.out.println("Streaming Texture to Off-Heap: " + path);

        ByteBuffer rawFileBuffer = streamToOffHeap(path);

        int[] width = new int[1];
        int[] height = new int[1];
        int[] channels = new int[1];

        stbi_set_flip_vertically_on_load(true);
        ByteBuffer decodedImage = stbi_load_from_memory(rawFileBuffer, width, height, channels, 4);

        if (decodedImage == null) {
            MemoryUtil.memFree(rawFileBuffer);
            throw new RuntimeException("STB failed to decode image: " + stbi_failure_reason());
        }
        MemoryUtil.memFree(rawFileBuffer);

        // [CHANGED: 2] We actually upload it to Vulkan now using your TextureLoader!
        // This returns the Bindless Array ID (e.g., 0, 1, 2...)
        int textureId = TextureLoader.uploadToGPU(decodedImage, width[0], height[0], commandPool);

        stbi_image_free(decodedImage);

        return textureId;
    }

    /**
     * Uploads raw decoded pixel data to the GPU using a Staging Buffer.
     * @param commandPool Required to allocate a temporary command buffer for the memory transfer.
     */
    public static int uploadToGPU(ByteBuffer decodedPixels, int width, int height, long commandPool) {
        long imageSize = (long) width * height * 4; // 4 bytes per pixel (RGBA)
        VkDevice device = Display.getDevice(); // Get device directly from your Display class

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // ==========================================
            // 1. CREATE AND FILL THE STAGING BUFFER
            // ==========================================
            long stagingBuffer = VulkanUtils.createBuffer(
                    imageSize,
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT
            );
            long stagingBufferMemory = VulkanUtils.allocateBufferMemory(
                    stagingBuffer,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );

            PointerBuffer data = stack.mallocPointer(1);
            vkMapMemory(device, stagingBufferMemory, 0, imageSize, 0, data);
            MemoryUtil.memCopy(MemoryUtil.memAddress(decodedPixels), data.get(0), imageSize);
            vkUnmapMemory(device, stagingBufferMemory);

            // ==========================================
            // 2. CREATE THE VULKAN IMAGE (DEVICE LOCAL)
            // ==========================================
            long vkImage = VulkanUtils.createImage(
                    width, height,
                    VK_FORMAT_R8G8B8A8_SRGB,
                    VK_IMAGE_TILING_OPTIMAL,
                    VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
            );

            long vkImageMemory = VulkanUtils.allocateImageMemory(
                    vkImage,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            );
            vkBindImageMemory(device, vkImage, vkImageMemory, 0);

            // ==========================================
            // 3. TRANSITIONS AND COPYING
            // ==========================================
            // Pass the command pool down so VulkanUtils can allocate the commands
            VkCommandBuffer commandBuffer = VulkanUtils.beginSingleTimeCommands(commandPool);

            VulkanUtils.transitionImageLayout(
                    commandBuffer, vkImage,
                    VK_FORMAT_R8G8B8A8_SRGB,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
            );

            VulkanUtils.copyBufferToImage(commandBuffer, stagingBuffer, vkImage, width, height);

            VulkanUtils.transitionImageLayout(
                    commandBuffer, vkImage,
                    VK_FORMAT_R8G8B8A8_SRGB,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            );

            VulkanUtils.endSingleTimeCommands(commandBuffer, commandPool);

            // ==========================================
            // 4. CLEANUP RAM & CREATE VIEWS
            // ==========================================
            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingBufferMemory, null);

            long imageView = VulkanUtils.createImageView(vkImage, VK_FORMAT_R8G8B8A8_SRGB);
            long sampler = VulkanUtils.createTextureSampler();

            // Store in our new registry!
            return TextureRegistry.add(vkImage, vkImageMemory, imageView, sampler);
        }
    }
}