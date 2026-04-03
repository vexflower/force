package renderer;

import hardware.VulkanContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import util.VK;
import java.nio.LongBuffer;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

public class RenderTarget {
    public int width, height, textureId = -1;
    public long vkFramebuffer, vkRenderPass, colorImage, colorImageMemory, colorImageView, sampler;
    public long depthImage, depthImageMemory, depthImageView;
    private VkRenderPassBeginInfo renderPassInfo;
    private VkClearValue.Buffer clearValues;

    public RenderTarget(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void init() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = VulkanContext.getDevice();
            int exactFormat = MasterRenderer.getSwapchainImageFormat();

            // 1. Color
            VkImageCreateInfo imageInfo = VK.createImageInfo(stack, width, height, exactFormat, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
            LongBuffer pImage = stack.mallocLong(1);
            vkCreateImage(device, imageInfo, null, pImage);
            colorImage = pImage.get(0);
            colorImageMemory = VK.allocateImageMemory(colorImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            vkBindImageMemory(device, colorImage, colorImageMemory, 0);
            colorImageView = VK.createImageView(colorImage, exactFormat);

            // 2. Depth
            VkImageCreateInfo depthInfo = VK.createImageInfo(stack, width, height, VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
            vkCreateImage(device, depthInfo, null, pImage);
            depthImage = pImage.get(0);
            depthImageMemory = VK.allocateImageMemory(depthImage, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            vkBindImageMemory(device, depthImage, depthImageMemory, 0);
            depthImageView = VK.createImageView(depthImage, VK_FORMAT_D32_SFLOAT);

            sampler = VK.createTextureSampler();
            this.textureId = loader.TextureRegistry.add(colorImage, colorImageMemory, colorImageView, sampler);
            shader.VKShader.updateBindlessTexture(this.textureId, colorImageView, sampler);

            // 3. Render Pass
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
            VK.modifyBufferAttachments(attachments, exactFormat);

            VkAttachmentReference.Buffer colorRef = VkAttachmentReference.calloc(1, stack).attachment(0).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            VkAttachmentReference depthRef = VkAttachmentReference.calloc(stack).attachment(1).layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1).pColorAttachments(colorRef).pDepthStencilAttachment(depthRef);

            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
            VK.modifyDependencies(stack, dependencies);

            VkRenderPassCreateInfo renderPassInfoCI = VkRenderPassCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments).pSubpasses(subpass).pDependencies(dependencies);

            LongBuffer pRenderPass = stack.mallocLong(1);
            vkCreateRenderPass(device, renderPassInfoCI, null, pRenderPass);
            vkRenderPass = pRenderPass.get(0);

            // 4. Framebuffer
            LongBuffer fbAttachments = stack.mallocLong(2).put(0, colorImageView).put(1, depthImageView);
            VkFramebufferCreateInfo fboInfo = VkFramebufferCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(vkRenderPass).pAttachments(fbAttachments).width(width).height(height).layers(1);
            vkCreateFramebuffer(device, fboInfo, null, pImage);
            vkFramebuffer = pImage.get(0);

            renderPassInfo = VkRenderPassBeginInfo.calloc().sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO).renderPass(vkRenderPass).framebuffer(vkFramebuffer);
            renderPassInfo.renderArea().offset().set(0, 0);
            renderPassInfo.renderArea().extent().set(width, height);
            clearValues = VkClearValue.calloc(2);
            renderPassInfo.pClearValues(clearValues);
        }
    }

    public void bind(VkCommandBuffer cmd, float r, float g, float b, float a) {
        clearValues.get(0).color().float32(0, r).float32(1, g).float32(2, b).float32(3, a);
        clearValues.get(1).depthStencil().depth(1.0f).stencil(0);
        vkCmdBeginRenderPass(cmd, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        VkViewport.Buffer viewport = VkViewport.calloc(1).x(0.0f).y(0.0f).width(width).height(height).minDepth(0.0f).maxDepth(1.0f);
        vkCmdSetViewport(cmd, 0, viewport);
        VkRect2D.Buffer scissor = VkRect2D.calloc(1);
        scissor.offset().set(0, 0);
        scissor.extent().set(width, height);
        vkCmdSetScissor(cmd, 0, scissor);
        viewport.free(); scissor.free();
    }

    public void unbind(VkCommandBuffer cmd) { vkCmdEndRenderPass(cmd); }

    public void destroy() {
        VkDevice device = VulkanContext.getDevice();
        if (vkFramebuffer != VK_NULL_HANDLE) {
            vkDestroyFramebuffer(device, vkFramebuffer, null);
            vkDestroyRenderPass(device, vkRenderPass, null);
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