package renderer;

import hardware.Display;
import model.Mesh;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class MasterRenderer {

    private static long swapchain;
    private static long[] swapchainImages;
    private static long[] swapchainImageViews;
    private static int swapchainImageFormat;
    private static VkExtent2D swapchainExtent = VkExtent2D.calloc();

    private static long renderPass;
    private static long[] framebuffers;

    public static final int MAX_FRAMES_IN_FLIGHT = 2;
    private static int currentFrame = 0;

    private static long commandPool;
    private static VkCommandBuffer[] commandBuffers;

    // --- SYNCHRONIZATION ---
    private static long[] imageAvailableSemaphores;
    private static long[] renderFinishedSemaphores;
    private static long[] inFlightFences;
    // [CHANGED: Tracks which image is tied to which frame's fence]
    private static long[] imagesInFlight;

    // Zero-GC Cache
    private static IntBuffer pImageIndex;
    private static VkCommandBufferBeginInfo beginInfo;
    private static VkRenderPassBeginInfo renderPassInfo;
    private static VkRect2D renderArea;
    private static VkClearValue.Buffer clearValues;
    private static VkSubmitInfo submitInfo;
    private static VkPresentInfoKHR presentInfo;
    private static LongBuffer pWaitSemaphores;
    private static IntBuffer pWaitDstStageMask;
    private static PointerBuffer pCommandBuffers;
    private static LongBuffer pSignalSemaphores;
    private static LongBuffer pSwapchains;

    public static void setRenderer() {
        System.out.println("Initializing Vulkan Master Renderer...");
        createSwapchain();
        createImageViews();
        createRenderPass();

        // === CRITICAL BINDLESS INJECTION ===
        shader.VKShader.initBindlessHardware(Display.getDevice());

        // Compile Shaders and link them to our Render Pass
        shader.VKShader.EntityShaderPipeline.pipeline = new shader.VKShader.EntityShaderPipeline(Display.getDevice(), renderPass, swapchainExtent);

        createFramebuffers();
        createCommandPool();

        Mesh.initPrimitives(commandPool);

        createCommandBuffer();
        createSyncObjects();

        initRenderLoopStructs();
    }

    private static void initRenderLoopStructs() {
        pImageIndex = MemoryUtil.memAllocInt(1);

        beginInfo = VkCommandBufferBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

        clearValues = VkClearValue.calloc(1);
        clearValues.color()
                .float32(0, 0.1f)
                .float32(1, 0.1f)
                .float32(2, 0.15f)
                .float32(3, 1.0f);

        renderArea = VkRect2D.calloc();
        renderArea.offset().set(0, 0);

        renderPassInfo = VkRenderPassBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass)
                .pClearValues(clearValues);

        pWaitSemaphores = MemoryUtil.memAllocLong(1);
        pWaitDstStageMask = MemoryUtil.memAllocInt(1).put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        pCommandBuffers = MemoryUtil.memAllocPointer(1);
        pSignalSemaphores = MemoryUtil.memAllocLong(1);
        pSwapchains = MemoryUtil.memAllocLong(1).put(0, swapchain);

        submitInfo = VkSubmitInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .waitSemaphoreCount(1)
                .pWaitDstStageMask(pWaitDstStageMask);

        presentInfo = VkPresentInfoKHR.calloc()
                .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                .swapchainCount(1)
                .pSwapchains(pSwapchains)
                .pImageIndices(pImageIndex);
    }

    private static void createSwapchain() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = Display.getDevice();
            VkPhysicalDevice physicalDevice = Display.getPhysicalDevice();
            long surface = Display.getSurface();

            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);

            if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
                swapchainExtent.width(capabilities.currentExtent().width());
                swapchainExtent.height(capabilities.currentExtent().height());
            } else {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(Display.getWindow(), width, height);
                swapchainExtent.width(width.get(0));
                swapchainExtent.height(height.get(0));
            }

            // ==========================================================
            // [CHANGED: TRIPLE BUFFERING (MAILBOX) LOGIC]
            // ==========================================================
            IntBuffer pPresentModeCount = stack.mallocInt(1);
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, null);
            IntBuffer pPresentModes = stack.mallocInt(pPresentModeCount.get(0));
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, pPresentModes);

            int presentMode = VK_PRESENT_MODE_FIFO_KHR; // Standard V-Sync Double Buffering (Fallback)
            for (int i = 0; i < pPresentModeCount.get(0); i++) {
                if (pPresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                    presentMode = VK_PRESENT_MODE_MAILBOX_KHR;
                    System.out.println("Triple Buffering (Mailbox) Enabled!");
                    break;
                }
            }

            // Force 3 images if the hardware allows it
            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                imageCount = capabilities.maxImageCount();
            } else if (imageCount < 3) {
                imageCount = 3;
            }

            swapchainImageFormat = VK_FORMAT_B8G8R8A8_UNORM;

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
            createInfo.surface(surface);
            createInfo.minImageCount(imageCount);
            createInfo.imageFormat(swapchainImageFormat);
            createInfo.imageColorSpace(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR);
            createInfo.imageExtent(swapchainExtent);
            createInfo.imageArrayLayers(1);
            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

            createInfo.presentMode(presentMode); // [CHANGED: Injected our Mailbox mode]
            createInfo.preTransform(capabilities.currentTransform());
            createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);
            createInfo.clipped(true);
            createInfo.oldSwapchain(VK_NULL_HANDLE);

            LongBuffer pSwapchain = stack.mallocLong(1);
            if (vkCreateSwapchainKHR(device, createInfo, null, pSwapchain) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan Swapchain!");
            }
            swapchain = pSwapchain.get(0);

            IntBuffer pImageCount = stack.mallocInt(1);
            vkGetSwapchainImagesKHR(device, swapchain, pImageCount, null);
            LongBuffer pSwapchainImages = stack.mallocLong(pImageCount.get(0));
            vkGetSwapchainImagesKHR(device, swapchain, pImageCount, pSwapchainImages);

            swapchainImages = new long[pImageCount.get(0)];
            for (int i = 0; i < swapchainImages.length; i++) {
                swapchainImages[i] = pSwapchainImages.get(i);
            }
        }
    }

    private static void createImageViews() {
        VkDevice device = Display.getDevice();
        swapchainImageViews = new long[swapchainImages.length];

        try (MemoryStack stack = stackPush()) {
            for (int i = 0; i < swapchainImages.length; i++) {
                VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack);
                createInfo.sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
                createInfo.image(swapchainImages[i]);
                createInfo.viewType(VK_IMAGE_VIEW_TYPE_2D);
                createInfo.format(swapchainImageFormat);
                createInfo.components().r(VK_COMPONENT_SWIZZLE_IDENTITY);
                createInfo.components().g(VK_COMPONENT_SWIZZLE_IDENTITY);
                createInfo.components().b(VK_COMPONENT_SWIZZLE_IDENTITY);
                createInfo.components().a(VK_COMPONENT_SWIZZLE_IDENTITY);
                createInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                createInfo.subresourceRange().baseMipLevel(0);
                createInfo.subresourceRange().levelCount(1);
                createInfo.subresourceRange().baseArrayLayer(0);
                createInfo.subresourceRange().layerCount(1);

                LongBuffer pImageView = stack.mallocLong(1);
                if (vkCreateImageView(device, createInfo, null, pImageView) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create Vulkan Image Views!");
                }
                swapchainImageViews[i] = pImageView.get(0);
            }
        }
    }

    private static void createRenderPass() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = Display.getDevice();

            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
            colorAttachment.format(swapchainImageFormat);
            colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
            colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR);
            colorAttachment.storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            colorAttachment.stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachment.stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE);
            colorAttachment.initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachment.finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack);
            colorAttachmentRef.attachment(0);
            colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);
            subpass.pColorAttachments(colorAttachmentRef);

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
            dependency.srcSubpass(VK_SUBPASS_EXTERNAL);
            dependency.dstSubpass(0);
            dependency.srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.srcAccessMask(0);
            dependency.dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            dependency.dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            renderPassInfo.pAttachments(colorAttachment);
            renderPassInfo.pSubpasses(subpass);
            renderPassInfo.pDependencies(dependency);

            LongBuffer pRenderPass = stack.mallocLong(1);
            if (vkCreateRenderPass(device, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan Render Pass!");
            }
            renderPass = pRenderPass.get(0);
        }
    }

    private static void createFramebuffers() {
        VkDevice device = Display.getDevice();
        framebuffers = new long[swapchainImageViews.length];

        try (MemoryStack stack = stackPush()) {
            LongBuffer attachments = stack.mallocLong(1);
            LongBuffer pFramebuffer = stack.mallocLong(1);

            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
            framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
            framebufferInfo.renderPass(renderPass);
            framebufferInfo.width(swapchainExtent.width());
            framebufferInfo.height(swapchainExtent.height());
            framebufferInfo.layers(1);

            for (int i = 0; i < swapchainImageViews.length; i++) {
                attachments.put(0, swapchainImageViews[i]);
                framebufferInfo.pAttachments(attachments);

                if (vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create Vulkan Framebuffer!");
                }
                framebuffers[i] = pFramebuffer.get(0);
            }
        }
    }

    private static void createCommandPool() {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(Display.getGraphicsQueueFamilyIndex());
            poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);
            if (vkCreateCommandPool(Display.getDevice(), poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool!");
            }
            commandPool = pCommandPool.get(0);
        }
    }

    private static void createCommandBuffer() {
        commandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];

        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
            allocInfo.commandPool(commandPool);
            allocInfo.level(VK_COMMAND_BUFFER_LEVEL_PRIMARY);
            allocInfo.commandBufferCount(MAX_FRAMES_IN_FLIGHT);

            PointerBuffer pCommandBuffers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
            if (vkAllocateCommandBuffers(Display.getDevice(), allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers!");
            }

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), Display.getDevice());
            }
        }
    }

    private static void createSyncObjects() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // [CHANGED: Reverted Semaphores back to MAX_FRAMES_IN_FLIGHT]
            imageAvailableSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
            renderFinishedSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
            inFlightFences = new long[MAX_FRAMES_IN_FLIGHT];

            // [CHANGED: Created the Khronos-Standard Image Tracker]
            imagesInFlight = new long[swapchainImages.length];

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                if (vkCreateSemaphore(Display.getDevice(), semaphoreInfo, null, pSemaphore) != VK_SUCCESS ||
                        vkCreateSemaphore(Display.getDevice(), semaphoreInfo, null, pSemaphore) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create semaphore!");
                }

                // We recreate the semaphore to get a fresh handle for the second array
                vkCreateSemaphore(Display.getDevice(), semaphoreInfo, null, pSemaphore);
                imageAvailableSemaphores[i] = pSemaphore.get(0);

                vkCreateSemaphore(Display.getDevice(), semaphoreInfo, null, pSemaphore);
                renderFinishedSemaphores[i] = pSemaphore.get(0);

                if (vkCreateFence(Display.getDevice(), fenceInfo, null, pFence) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create fence!");
                }
                inFlightFences[i] = pFence.get(0);
            }
        }
    }

    public static void render(RenderState state) {
        VkDevice device = Display.getDevice();

        // 1. Wait for the CPU frame to finish executing commands on the GPU
        vkWaitForFences(device, inFlightFences[currentFrame], true, -1);

        int acquireResult = vkAcquireNextImageKHR(device, swapchain, -1, imageAvailableSemaphores[currentFrame], VK_NULL_HANDLE, pImageIndex);
        if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
            recreateSwapchain();
            return;
        }

        int imageIndex = pImageIndex.get(0);

        // ==========================================================
        // [CHANGED: THE KHRONOS BULLETPROOF SYNC FIX]
        // ==========================================================
        // Check if the physical image we just acquired is still tied to a previous frame's fence.
        // If it is, we MUST wait for that previous frame to finish before we start overwriting it!
        if (imagesInFlight[imageIndex] != VK_NULL_HANDLE) {
            vkWaitForFences(device, imagesInFlight[imageIndex], true, -1);
        }

        // Mark the image as "In Flight" by assigning it our current frame's fence
        imagesInFlight[imageIndex] = inFlightFences[currentFrame];

        // Now it is finally safe to reset our fence and start recording!
        vkResetFences(device, inFlightFences[currentFrame]);
        vkResetCommandBuffer(commandBuffers[currentFrame], 0);

        if (vkBeginCommandBuffer(commandBuffers[currentFrame], beginInfo) != VK_SUCCESS) {
            throw new RuntimeException("Failed to begin recording command buffer!");
        }

        renderArea.offset().set(0, 0);
        renderArea.extent(swapchainExtent);
        renderPassInfo.renderArea(renderArea);
        renderPassInfo.pClearValues(clearValues);

        renderPassInfo.framebuffer(framebuffers[imageIndex]);
        vkCmdBeginRenderPass(commandBuffers[currentFrame], renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

        shader.VKShader.bind(shader.VKShader.EntityShaderPipeline.pipeline, commandBuffers[currentFrame]);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindDescriptorSets(commandBuffers[currentFrame], VK_PIPELINE_BIND_POINT_GRAPHICS,
                    shader.VKShader.EntityShaderPipeline.pipeline.pipelineLayout,
                    0, stack.longs(shader.VKShader.bindlessDescriptorSet), null);

            for (int i = 0; i < state.entityCount; i++) {
                int meshId = state.meshIds[i];
                int texId = state.textureIds[i];

                LongBuffer vertexBuffers = stack.longs(loader.MeshLoader.getVertexBuffer(meshId), loader.MeshLoader.getUvBuffer(meshId));
                LongBuffer offsets = stack.longs(0, 0);
                vkCmdBindVertexBuffers(commandBuffers[currentFrame], 0, vertexBuffers, offsets);
                vkCmdBindIndexBuffer(commandBuffers[currentFrame], loader.MeshLoader.getIndexBuffer(meshId), 0, VK_INDEX_TYPE_UINT32);

                shader.VKShader.EntityShaderPipeline.loadTransformationMatrixArray(commandBuffers[currentFrame], state.transforms, i * 16);
                vkCmdPushConstants(commandBuffers[currentFrame], shader.VKShader.EntityShaderPipeline.pipeline.pipelineLayout,
                        VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 64, stack.ints(texId));

                vkCmdDrawIndexed(commandBuffers[currentFrame], loader.MeshLoader.getIndexCount(meshId), 1, 0, 0, 0);
            }
        }

        vkCmdEndRenderPass(commandBuffers[currentFrame]);

        if (vkEndCommandBuffer(commandBuffers[currentFrame]) != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer!");
        }

        // ==========================================================
        // [CHANGED: We can safely use currentFrame for both now!]
        // ==========================================================
        submitInfo.pWaitSemaphores(pWaitSemaphores.put(0, imageAvailableSemaphores[currentFrame]));
        submitInfo.pCommandBuffers(pCommandBuffers.put(0, commandBuffers[currentFrame]));
        submitInfo.pSignalSemaphores(pSignalSemaphores.put(0, renderFinishedSemaphores[currentFrame]));

        if (vkQueueSubmit(Display.getGraphicsQueue(), submitInfo, inFlightFences[currentFrame]) != VK_SUCCESS) {
            throw new RuntimeException("Failed to submit draw command buffer!");
        }

        presentInfo.pWaitSemaphores(pSignalSemaphores);
        presentInfo.pImageIndices(pImageIndex);

        vkQueuePresentKHR(Display.getPresentQueue(), presentInfo);

        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
    }

    private static void cleanupSwapchain() {
        VkDevice device = Display.getDevice();

        for (long framebuffer : framebuffers) {
            vkDestroyFramebuffer(device, framebuffer, null);
        }

        if (shader.VKShader.EntityShaderPipeline.pipeline != null) {
            vkDestroyPipeline(device, shader.VKShader.EntityShaderPipeline.pipeline.graphicsPipeline, null);
            vkDestroyPipelineLayout(device, shader.VKShader.EntityShaderPipeline.pipeline.pipelineLayout, null);
            shader.VKShader.EntityShaderPipeline.pipeline = null;
        }

        if (renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, renderPass, null);
        }

        for (long imageView : swapchainImageViews) {
            vkDestroyImageView(device, imageView, null);
        }

        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, null);
        }
    }

    private static void recreateSwapchain() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            java.nio.IntBuffer width = stack.mallocInt(1);
            java.nio.IntBuffer height = stack.mallocInt(1);

            org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(Display.getWindow(), width, height);
            while (width.get(0) == 0 || height.get(0) == 0) {
                org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(Display.getWindow(), width, height);
                org.lwjgl.glfw.GLFW.glfwWaitEvents();
            }
        }

        VkDevice device = Display.getDevice();
        vkDeviceWaitIdle(device);
        cleanupSwapchain();

        createSwapchain();
        createImageViews();
        createRenderPass();

        // [CHANGED: Refresh the imagesInFlight array to match new swapchain size]
        imagesInFlight = new long[swapchainImages.length];

        shader.VKShader.EntityShaderPipeline.pipeline = new shader.VKShader.EntityShaderPipeline(device, renderPass, swapchainExtent);
        createFramebuffers();

        if (renderPassInfo != null) {
            renderPassInfo.renderPass(renderPass);
            pSwapchains.put(0, swapchain);
        }
    }

    public static void destroy() {
        VkDevice device = Display.getDevice();
        if (device != null) {
            vkDeviceWaitIdle(device);

            if (shader.VKShader.bindlessDescriptorPool != VK_NULL_HANDLE) {
                vkDestroyDescriptorPool(device, shader.VKShader.bindlessDescriptorPool, null);
                vkDestroyDescriptorSetLayout(device, shader.VKShader.bindlessDescriptorSetLayout, null);
            }

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                vkDestroySemaphore(device, renderFinishedSemaphores[i], null);
                vkDestroySemaphore(device, imageAvailableSemaphores[i], null);
                vkDestroyFence(device, inFlightFences[i], null);
            }
            vkDestroyCommandPool(device, commandPool, null);

            cleanupSwapchain();
            swapchainExtent.free();
        }

        if(beginInfo != null) {
            beginInfo.free();
            clearValues.free();
            renderArea.free();
            renderPassInfo.free();
            submitInfo.free();
            presentInfo.free();

            MemoryUtil.memFree(pImageIndex);
            MemoryUtil.memFree(pWaitSemaphores);
            MemoryUtil.memFree(pWaitDstStageMask);
            MemoryUtil.memFree(pCommandBuffers);
            MemoryUtil.memFree(pSignalSemaphores);
            MemoryUtil.memFree(pSwapchains);
        }
    }

    public static long getCommandPool() {
        return commandPool;
    }
}