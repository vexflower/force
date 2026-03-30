package ui;

import hardware.Display;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import util.VulkanUtils;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The Vulkan Off-Screen Render Target.
 * Any class extending this gets its own private texture in GPU memory,
 * automatically mapped to the Bindless Texture Phonebook.
 */
public class FrameBufferObject {
    public int width;
    public int height;

    // The Bindless ID! Used to draw this FBO onto a 3D plane or a UI quad.
    public int textureId = -1;

    // 64-bit Vulkan Pointers
    public long vkFramebuffer;
    public long vkRenderPass;
    public long colorImage;
    public long colorImageMemory;
    public long colorImageView;
    // ... right below your existing fields ...
    public long sampler;

    // --- NEW: FBO Background Color State ---
    public float bgR = 0.0f;
    public float bgG = 0.0f;
    public float bgB = 0.0f;
    public float bgA = 0.0f; // Defaults to transparent

    private VkRenderPassBeginInfo renderPassInfo;
    private VkClearValue.Buffer clearValues;

    public FrameBufferObject(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Initializes the FBO on the GPU. Does not require a CommandPool because
     * the layout transitions happen automatically via the Render Pass dependencies.
     */
    public void init() {
        VkDevice device = Display.getDevice();

        // 1. Create the physical image and memory (Using UNORM for UI to avoid double-gamma correction)
        int format = VK_FORMAT_R8G8B8A8_UNORM;
        colorImage = VulkanUtils.createImage(width, height, format, VK_IMAGE_TILING_OPTIMAL,
                VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
        colorImageMemory = VulkanUtils.allocateImageMemory(colorImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        vkBindImageMemory(device, colorImage, colorImageMemory, 0);

        // 2. Create the View and Sampler
        colorImageView = VulkanUtils.createImageView(colorImage, format);
        sampler = VulkanUtils.createTextureSampler();

        // 3. Register into the Global Bindless Array!
        textureId = loader.TextureRegistry.add(colorImage, colorImageMemory, colorImageView, sampler);
        shader.VKShader.updateBindlessTexture(textureId, colorImageView, sampler);

        // 4. Create the FBO-specific Render Pass
        createRenderPass(device, format);

        // 5. Create the Framebuffer linking the Image to the Render Pass
        createFramebuffer(device);

        // 6. Pre-allocate Render Pass structs for Zero-GC looping
        initRenderStructs();
    }

    private void createRenderPass(VkDevice device, int format) {
        try (MemoryStack stack = stackPush()) {
            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
            colorAttachment.format(format);
            colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
            colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR); // Clear old UI every frame
            colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE); // Keep the result for sampling
            colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
            colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachment.finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL); // Ready to be drawn as a texture!

            VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack);
            colorAttachmentRef.attachment(0);
            colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);
            subpass.pColorAttachments(colorAttachmentRef);

            // Critical: Ensure the main pass waits for this FBO to finish drawing before trying to sample it
            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(2, stack);
            dependency.get(0).srcSubpass(VK_SUBPASS_EXTERNAL);
            dependency.get(0).dstSubpass(0);
            dependency.get(0).srcStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
            dependency.get(0).dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.get(0).srcAccessMask(VK_ACCESS_SHADER_READ_BIT);
            dependency.get(0).dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            dependency.get(1).srcSubpass(0);
            dependency.get(1).dstSubpass(VK_SUBPASS_EXTERNAL);
            dependency.get(1).srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.get(1).dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
            dependency.get(1).srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
            dependency.get(1).dstAccessMask(VK_ACCESS_SHADER_READ_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            renderPassInfo.pAttachments(colorAttachment);
            renderPassInfo.pSubpasses(subpass);
            renderPassInfo.pDependencies(dependency);

            LongBuffer pRenderPass = stack.mallocLong(1);
            if (vkCreateRenderPass(device, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create FBO Render Pass!");
            }
            vkRenderPass = pRenderPass.get(0);
        }
    }

    private void createFramebuffer(VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
            framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
            framebufferInfo.renderPass(vkRenderPass);
            framebufferInfo.pAttachments(stack.longs(colorImageView));
            framebufferInfo.width(width);
            framebufferInfo.height(height);
            framebufferInfo.layers(1);

            LongBuffer pFramebuffer = stack.mallocLong(1);
            if (vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create FBO Framebuffer!");
            }
            vkFramebuffer = pFramebuffer.get(0);
        }
    }

    // ... scroll down to initRenderStructs() ...

    private void initRenderStructs() {
        // Pre-allocate structs to avoid GC allocations during the render loop
        clearValues = VkClearValue.calloc(1);

        // [CHANGED]: Use the dynamic color state instead of hardcoded 0.0f
        clearValues.color().float32(0, bgR).float32(1, bgG).float32(2, bgB).float32(3, bgA);

        renderPassInfo = VkRenderPassBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(vkRenderPass)
                .framebuffer(vkFramebuffer)
                .pClearValues(clearValues);

        renderPassInfo.renderArea().offset().set(0, 0);
        renderPassInfo.renderArea().extent().set(width, height);
    }
    public void bind(VkCommandBuffer cmd) {
        vkCmdBeginRenderPass(cmd, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

        // Update viewport/scissor to match the FBO dimensions
        VkViewport.Buffer viewport = VkViewport.calloc(1).x(0.0f).y(0.0f).width(width).height(height).minDepth(0.0f).maxDepth(1.0f);
        vkCmdSetViewport(cmd, 0, viewport);

        VkRect2D.Buffer scissor = VkRect2D.calloc(1);
        scissor.offset().set(0, 0);
        scissor.extent().set(width, height);
        vkCmdSetScissor(cmd, 0, scissor);

        viewport.free();
        scissor.free();
    }

    /**
     * Zero-GC color update. Directly mutates the off-heap Vulkan struct.
     */
    public void setBackgroundColor(float r, float g, float b, float a) {
        this.bgR = r;
        this.bgG = g;
        this.bgB = b;
        this.bgA = a;

        // If the FBO is already initialized, update the C-struct instantly
        if (clearValues != null) {
            clearValues.color().float32(0, r).float32(1, g).float32(2, b).float32(3, a);
        }
    }

    public void unbind(VkCommandBuffer cmd) {
        vkCmdEndRenderPass(cmd);
    }

    public void destroy() {
        VkDevice device = Display.getDevice();
        vkDestroyFramebuffer(device, vkFramebuffer, null);
        vkDestroyRenderPass(device, vkRenderPass, null);

        if (renderPassInfo != null) {
            renderPassInfo.free();
            clearValues.free();
        }

        // Note: Image, Memory, View, and Sampler are handled by your TextureRegistry.destroy() [cite: 844-848]
    }
}