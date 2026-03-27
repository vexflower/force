package loader;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;

import static org.lwjgl.vulkan.VK12.*;

public class TextureLoader {

    // Assuming you have a central Vulkan context class holding your logical device and physical device
    private static final VkDevice device = VulkanContext.getDevice();

    /**
     * Uploads raw decoded pixel data to the GPU using a Staging Buffer.
     */
    public static int uploadToGPU(ByteBuffer decodedPixels, int width, int height) {
        long imageSize = (long) width * height * 4; // 4 bytes per pixel (RGBA)

        try (MemoryStack stack = MemoryStack.stackPush()) {

            // ==========================================
            // 1. CREATE AND FILL THE STAGING BUFFER
            // ==========================================
            // Create a buffer in CPU-visible RAM
            long stagingBuffer = VulkanUtils.createBuffer(
                    imageSize,
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT
            );
            long stagingBufferMemory = VulkanUtils.allocateBufferMemory(
                    stagingBuffer,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            );

            // Map the Vulkan memory to a Java pointer and blast the pixels in
            PointerBuffer data = stack.mallocPointer(1);
            vkMapMemory(device, stagingBufferMemory, 0, imageSize, 0, data);
            MemoryUtil.memCopy(MemoryUtil.memAddress(decodedPixels), data.get(0), imageSize);
            vkUnmapMemory(device, stagingBufferMemory);

            // ==========================================
            // 2. CREATE THE VULKAN IMAGE (DEVICE LOCAL)
            // ==========================================
            // Create the optimal GPU image object
            long vkImage = VulkanUtils.createImage(
                    width, height,
                    VK_FORMAT_R8G8B8A8_SRGB,
                    VK_IMAGE_TILING_OPTIMAL,
                    VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
            );

            // Allocate the fastest memory the Intel UHD can provide and bind it
            long vkImageMemory = VulkanUtils.allocateImageMemory(
                    vkImage,
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            );
            vkBindImageMemory(device, vkImage, vkImageMemory, 0);

            // ==========================================
            // 3. TRANSITIONS AND COPYING
            // ==========================================
            // We use a single-use command buffer to perform the transfers on the GPU
            VkCommandBuffer commandBuffer = VulkanUtils.beginSingleTimeCommands();

            // Transition from UNDEFINED to receiving data
            VulkanUtils.transitionImageLayout(
                    commandBuffer, vkImage,
                    VK_FORMAT_R8G8B8A8_SRGB,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
            );

            // Copy the buffer to the image
            VulkanUtils.copyBufferToImage(commandBuffer, stagingBuffer, vkImage, width, height);

            // Transition from receiving data to shader-readable
            VulkanUtils.transitionImageLayout(
                    commandBuffer, vkImage,
                    VK_FORMAT_R8G8B8A8_SRGB,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            );

            VulkanUtils.endSingleTimeCommands(commandBuffer);

            // ==========================================
            // 4. CLEANUP RAM & CREATE VIEWS
            // ==========================================
            // Free the CPU staging buffer immediately to keep memory footprint low!
            vkDestroyBuffer(device, stagingBuffer, null);
            vkFreeMemory(device, stagingBufferMemory, null);

            // Create the ImageView (how the shader interprets the memory)
            long imageView = VulkanUtils.createImageView(vkImage, VK_FORMAT_R8G8B8A8_SRGB);

            // Create the Sampler (filtering, wrapping, anisotropic settings)
            long sampler = VulkanUtils.createTextureSampler();

            // Register this complete texture package and return an integer ID for your RendererManager
            return TextureRegistry.add(vkImage, vkImageMemory, imageView, sampler);
        }
    }
}