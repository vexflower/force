package ui;

import hardware.Display;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import renderer.MasterRenderer;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The Vulkan Off-Screen Render Target (True Viewport).
 * Features a private Color and Depth buffer for isolated 3D sorting.
 */
public class FrameBufferObject {
    public int width;
    public int height;

    public int textureId = -1;

    public long vkFramebuffer;
    public long vkRenderPass;

    // Private Color Buffer
    public long colorImage;
    public long colorImageMemory;
    public long colorImageView;
    public long sampler;

    // --- NEW: Private Depth Buffer ---
    public long depthImage;
    public long depthImageMemory;
    public long depthImageView;

    public float bgR = 0.0f;
    public float bgG = 0.0f;
    public float bgB = 0.0f;
    public float bgA = 0.0f;

    private VkRenderPassBeginInfo renderPassInfo;
    private VkClearValue.Buffer clearValues;

    public FrameBufferObject(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void init() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = Display.getDevice();

            int exactFormat = MasterRenderer.getSwapchainImageFormat();

            // 1. Create Color Image (Standard RGBA)
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .extent(VkExtent3D.calloc(stack).set(width, height, 1))
                    .mipLevels(1).arrayLayers(1)
                    .format(exactFormat)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK_SAMPLE_COUNT_1_BIT);

            LongBuffer pImage = stack.mallocLong(1);
            vkCreateImage(device, imageInfo, null, pImage);
            colorImage = pImage.get(0);

            // Allocate Color Memory
            VkMemoryRequirements memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, colorImage, memReqs);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            LongBuffer pMemory = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMemory);
            colorImageMemory = pMemory.get(0);
            vkBindImageMemory(device, colorImage, colorImageMemory, 0);

            // Create Color View
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(colorImage).viewType(VK_IMAGE_VIEW_TYPE_2D).format(exactFormat)
                    .subresourceRange(r -> r.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1));

            LongBuffer pView = stack.mallocLong(1);
            vkCreateImageView(device, viewInfo, null, pView);
            colorImageView = pView.get(0);

            // --- NEW: 1.5 Create Private Depth Image ---
            VkImageCreateInfo depthInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .extent(VkExtent3D.calloc(stack).set(width, height, 1))
                    .mipLevels(1).arrayLayers(1)
                    .format(VK_FORMAT_D32_SFLOAT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK_SAMPLE_COUNT_1_BIT);

            vkCreateImage(device, depthInfo, null, pImage);
            depthImage = pImage.get(0);

            vkGetImageMemoryRequirements(device, depthImage, memReqs);
            allocInfo.allocationSize(memReqs.size());
            allocInfo.memoryTypeIndex(findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            vkAllocateMemory(device, allocInfo, null, pMemory);
            depthImageMemory = pMemory.get(0);
            vkBindImageMemory(device, depthImage, depthImageMemory, 0);

            VkImageViewCreateInfo depthViewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(depthImage).viewType(VK_IMAGE_VIEW_TYPE_2D).format(VK_FORMAT_D32_SFLOAT)
                    .subresourceRange(r -> r.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).levelCount(1).layerCount(1));

            vkCreateImageView(device, depthViewInfo, null, pView);
            depthImageView = pView.get(0);

            // Create Sampler for the Bindless Array
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_LINEAR).minFilter(VK_FILTER_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR);

            LongBuffer pSampler = stack.mallocLong(1);
            vkCreateSampler(device, samplerInfo, null, pSampler);
            sampler = pSampler.get(0);

            // Register into the global texture array
            this.textureId = loader.TextureRegistry.add(colorImage, colorImageMemory, colorImageView, sampler);

            // ---> [THE FIX]: Tell the GPU's Descriptor Set that this FBO texture exists! <---
            shader.VKShader.updateBindlessTexture(this.textureId, colorImageView, sampler);

            // 2. Create the Off-Screen Render Pass (Now with TWO attachments)
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);

            // Color Attachment (Formats it so the shader can read it as a texture later)
            // [THE FIX]: Change VK_FORMAT_R8G8B8A8_UNORM to exactFormat here too!
            attachments.get(0).format(exactFormat).samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            // Depth Attachment
            attachments.get(1).format(VK_FORMAT_D32_SFLOAT).samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack)
                    .attachment(0).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkAttachmentReference depthRef = VkAttachmentReference.calloc(stack)
                    .attachment(1).layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorRef)
                    .pDepthStencilAttachment(depthRef); // Bind private depth buffer

            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
            // Dependency 0: Coming IN to the FBO
            dependencies.get(0).srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    // [THE FIX]: Added EARLY_FRAGMENT_TESTS_BIT
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    // [THE FIX]: Added DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            // Dependency 1: Going OUT of the FBO
            dependencies.get(1).srcSubpass(0).dstSubpass(VK_SUBPASS_EXTERNAL)
                    // [THE FIX]: Added LATE_FRAGMENT_TESTS_BIT
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    // [THE FIX]: Added DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            VkRenderPassCreateInfo renderPassInfoCI = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(dependencies);

            LongBuffer pRenderPass = stack.mallocLong(1);
            vkCreateRenderPass(device, renderPassInfoCI, null, pRenderPass);
            vkRenderPass = pRenderPass.get(0);

            // 3. Create the Framebuffer (Binding both Color and Depth views)
            LongBuffer fbAttachments = stack.mallocLong(2);
            fbAttachments.put(0, colorImageView);
            fbAttachments.put(1, depthImageView); // Inject depth!

            VkFramebufferCreateInfo fboInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(vkRenderPass)
                    .pAttachments(fbAttachments)
                    .width(width).height(height).layers(1);

            LongBuffer pFramebuffer = stack.mallocLong(1);
            vkCreateFramebuffer(device, fboInfo, null, pFramebuffer);
            vkFramebuffer = pFramebuffer.get(0);

            // 4. Pre-allocate the RenderPass Begin Info (Zero-GC execution)
            renderPassInfo = VkRenderPassBeginInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(vkRenderPass)
                    .framebuffer(vkFramebuffer);
            renderPassInfo.renderArea().offset().set(0, 0);
            renderPassInfo.renderArea().extent().set(width, height);

            // Allocate 2 Clear Values (Color and Depth)
            clearValues = VkClearValue.calloc(2);
            clearValues.get(0).color().float32(0, bgR).float32(1, bgG).float32(2, bgB).float32(3, bgA);
            clearValues.get(1).depthStencil().depth(1.0f).stencil(0);
            renderPassInfo.pClearValues(clearValues);
        }
    }

    // Helper method to find correct GPU memory type
    private int findMemoryType(int typeFilter, int properties) {
        VkPhysicalDeviceMemoryProperties memProperties = VkPhysicalDeviceMemoryProperties.calloc();
        vkGetPhysicalDeviceMemoryProperties(Display.getPhysicalDevice(), memProperties);
        for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
            if ((typeFilter & (1 << i)) != 0 && (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                memProperties.free();
                return i;
            }
        }
        memProperties.free();
        throw new RuntimeException("Failed to find suitable memory type for FBO!");
    }

    public void bind(VkCommandBuffer cmd) {
        // ---> [THE FIX]: Push the background colors into the struct right before rendering!
        clearValues.get(0).color().float32(0, bgR).float32(1, bgG).float32(2, bgB).float32(3, bgA);
        vkCmdBeginRenderPass(cmd, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

        VkViewport.Buffer viewport = VkViewport.calloc(1).x(0.0f).y(0.0f).width(width).height(height).minDepth(0.0f).maxDepth(1.0f);
        vkCmdSetViewport(cmd, 0, viewport);

        VkRect2D.Buffer scissor = VkRect2D.calloc(1);
        scissor.offset().set(0, 0);
        scissor.extent().set(width, height);
        vkCmdSetScissor(cmd, 0, scissor);

        viewport.free();
        scissor.free();
    }

    public void setBackgroundColor(float r, float g, float b, float a) {
        this.bgR = r;
        this.bgG = g;
        this.bgB = b;
        this.bgA = a;
    }

    public void unbind(VkCommandBuffer cmd) {
        vkCmdEndRenderPass(cmd);
    }

    public void destroy() {
        VkDevice device = Display.getDevice();
        if (vkFramebuffer != VK_NULL_HANDLE) {
            vkDestroyFramebuffer(device, vkFramebuffer, null);
            vkDestroyRenderPass(device, vkRenderPass, null);

            // [THE FIX]: DELETE the 4 lines that used to be here!
            // TextureRegistry owns the color texture now, so it will safely destroy them on shutdown.

            // Cleanup Private Depth Buffer (We keep this because it IS NOT in the registry)
            vkDestroyImageView(device, depthImageView, null);
            vkDestroyImage(device, depthImage, null);
            vkFreeMemory(device, depthImageMemory, null);

            if (clearValues != null) clearValues.free();
            if (renderPassInfo != null) renderPassInfo.free();

            vkFramebuffer = VK_NULL_HANDLE;
            this.textureId = -1;
        }
    }
}