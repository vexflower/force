package renderer;
import hardware.Display;
import lang.Mat4;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class MasterRenderer {

    // Vulkan Swapchain State
    private static long swapchain;
    private static long[] swapchainImages;
    private static long[] swapchainImageViews;
    private static int swapchainImageFormat;
    // FIX: Allocate this permanently in global off-heap memory!
    private static VkExtent2D swapchainExtent = VkExtent2D.calloc();
    // Phase 2 State
    private static long renderPass;
    private static long[] framebuffers;

    // =========================================================================================
    // PHASE 3 & 4 State (UPDATED FOR FRAMES IN FLIGHT)
    // =========================================================================================
    public static final int MAX_FRAMES_IN_FLIGHT = 2;
    private static int currentFrame = 0;

    private static long commandPool;
    private static VkCommandBuffer[] commandBuffers; // Array of buffers
    private static long[] imageAvailableSemaphores;  // Array of handles
    private static long[] renderFinishedSemaphores;
    private static long[] inFlightFences;

    /**
     * Boot up the Vulkan Rendering Pipeline.
     */
    public static void setRenderer() {
        System.out.println("Initializing Vulkan Master Renderer...");
        createSwapchain();
        createImageViews();
        createRenderPass();

        // Compile Shaders and link them to our Render Pass
        shader.VKShader.EntityShaderPipeline.pipeline = new shader.VKShader.EntityShaderPipeline(Display.getDevice(), renderPass, swapchainExtent);

        createFramebuffers();
        createCommandPool();
        createCommandBuffer();
        createSyncObjects();
    }

    private static void createSwapchain() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = Display.getDevice();
            VkPhysicalDevice physicalDevice = Display.getPhysicalDevice();
            long surface = Display.getSurface();

            // 1. Get Surface Capabilities (Min/Max images, width/height limits)
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);

            // If the hardware surface dictates the size, we MUST use it (Standard for macOS/Retina)
            if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
                swapchainExtent.width(capabilities.currentExtent().width());
                swapchainExtent.height(capabilities.currentExtent().height());
            } else {
                // Fallback: Manually query the actual pixel count from GLFW
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(Display.getWindow(), width, height);
                swapchainExtent.width(width.get(0));
                swapchainExtent.height(height.get(0));
            }

            // 3. Request the number of images (Double Buffering = 2, Triple Buffering = 3)
            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                imageCount = capabilities.maxImageCount();
            }

            // 4. Hardcode standard color format (Standard 32-bit RGBA)
            swapchainImageFormat = VK_FORMAT_B8G8R8A8_UNORM;

            // 5. Build the Swapchain Creation Struct
            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);
            createInfo.surface(surface);
            createInfo.minImageCount(imageCount);
            createInfo.imageFormat(swapchainImageFormat);
            createInfo.imageColorSpace(VK_COLOR_SPACE_SRGB_NONLINEAR_KHR);
            createInfo.imageExtent(swapchainExtent);
            createInfo.imageArrayLayers(1);
            createInfo.imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT); // We will draw colors to it

            createInfo.presentMode(VK_PRESENT_MODE_IMMEDIATE_KHR);
            createInfo.preTransform(capabilities.currentTransform());
            createInfo.compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR); // Ignore window transparency
            createInfo.clipped(true);
            createInfo.oldSwapchain(VK_NULL_HANDLE);

            // 6. Tell the GPU to create the Swapchain
            LongBuffer pSwapchain = stack.mallocLong(1);
            if (vkCreateSwapchainKHR(device, createInfo, null, pSwapchain) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan Swapchain!");
            }
            swapchain = pSwapchain.get(0);

            // 7. Extract the raw Image handles generated by the Swapchain
            IntBuffer pImageCount = stack.mallocInt(1);
            vkGetSwapchainImagesKHR(device, swapchain, pImageCount, null);
            LongBuffer pSwapchainImages = stack.mallocLong(pImageCount.get(0));
            vkGetSwapchainImagesKHR(device, swapchain, pImageCount, pSwapchainImages);

            swapchainImages = new long[pImageCount.get(0)];
            for (int i = 0; i < swapchainImages.length; i++) {
                swapchainImages[i] = pSwapchainImages.get(i);
            }

            System.out.println("Vulkan Swapchain created with " + swapchainImages.length + " images.");
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

                // Map color channels purely (R to R, G to G, etc.)
                createInfo.components().r(VK_COMPONENT_SWIZZLE_IDENTITY);
                createInfo.components().g(VK_COMPONENT_SWIZZLE_IDENTITY);
                createInfo.components().b(VK_COMPONENT_SWIZZLE_IDENTITY);
                createInfo.components().a(VK_COMPONENT_SWIZZLE_IDENTITY);

                // No mipmapping, just 1 base color layer
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
        System.out.println("Vulkan Image Views successfully mapped.");
    }

    // =========================================================================================
    // PHASE 2: RENDER PASS & FRAMEBUFFERS
    // =========================================================================================

    private static void createRenderPass() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = Display.getDevice();

            VkAttachmentDescription.Buffer colorAttachment = VkAttachmentDescription.calloc(1, stack);
            colorAttachment.format(swapchainImageFormat);
            colorAttachment.samples(VK_SAMPLE_COUNT_1_BIT);
            colorAttachment.loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR); // Clear screen to black
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
            System.out.println("Vulkan Render Pass established.");
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
            System.out.println("Vulkan Framebuffers linked successfully.");
        }
    }

    // =========================================================================================
    // PHASE 3 & 4: COMMANDS & SYNC
    // =========================================================================================

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
            allocInfo.commandBufferCount(MAX_FRAMES_IN_FLIGHT); // Allocate 2 at once

            org.lwjgl.PointerBuffer pCommandBuffers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
            if (vkAllocateCommandBuffers(Display.getDevice(), allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers!");
            }

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), Display.getDevice());
            }
        }
    }

    private static void createSyncObjects() {
        imageAvailableSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
        renderFinishedSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
        inFlightFences = new long[MAX_FRAMES_IN_FLIGHT];

        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT); // Start signaled so the first frame doesn't block forever

            LongBuffer pSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);
            VkDevice device = Display.getDevice();

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                if (vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create semaphore!");
                }
                imageAvailableSemaphores[i] = pSemaphore.get(0);

                if (vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create semaphore!");
                }
                renderFinishedSemaphores[i] = pSemaphore.get(0);

                if (vkCreateFence(device, fenceInfo, null, pFence) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create fence!");
                }
                inFlightFences[i] = pFence.get(0);
            }
        }
    }

    public static void render(Mat4 transform) {
        VkDevice device = Display.getDevice();

        // 1. Wait for the CURRENT frame's fence to open
        vkWaitForFences(device, inFlightFences[currentFrame], true, ~0L);

        try (MemoryStack stack = stackPush()) {
            IntBuffer pImageIndex = stack.mallocInt(1);

            // 2. Acquire image (ONLY CALLED ONCE)
            int result = vkAcquireNextImageKHR(device, swapchain, ~0L, imageAvailableSemaphores[currentFrame], VK_NULL_HANDLE, pImageIndex);

            if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                // The window resized or shifted. The frame is dead
                recreateSwapchain();
                return;
            } else if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                throw new RuntimeException("Failed to acquire swapchain image!");
            }

            int imageIndex = pImageIndex.get(0);

            // 3. Reset the fence BEFORE submitting the command buffer
            vkResetFences(device, inFlightFences[currentFrame]);

            VkCommandBuffer currentCmdBuffer = commandBuffers[currentFrame];
            vkResetCommandBuffer(currentCmdBuffer, 0);

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            vkBeginCommandBuffer(currentCmdBuffer, beginInfo);

            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);
            renderPassInfo.renderPass(renderPass);
            renderPassInfo.framebuffer(framebuffers[imageIndex]);

            VkRect2D renderArea = VkRect2D.calloc(stack);
            renderArea.offset(VkOffset2D.calloc(stack).set(0, 0));
            renderArea.extent(swapchainExtent);
            renderPassInfo.renderArea(renderArea);

            VkClearValue.Buffer clearValues = VkClearValue.calloc(1, stack);
            clearValues.color().float32(stack.floats(0.1f, 0.1f, 0.15f, 1.0f));
            renderPassInfo.pClearValues(clearValues);

            vkCmdBeginRenderPass(currentCmdBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

            shader.VKShader.bind(shader.VKShader.EntityShaderPipeline.pipeline, currentCmdBuffer);
            shader.VKShader.EntityShaderPipeline.loadTransformationMatrix(currentCmdBuffer, transform);

            vkCmdDraw(currentCmdBuffer, 3, 1, 0, 0);

            vkCmdEndRenderPass(currentCmdBuffer);
            if (vkEndCommandBuffer(currentCmdBuffer) != VK_SUCCESS) {
                throw new RuntimeException("Failed to record command buffer!");
            }

            // 4. Submit using CURRENT frame's sync objects
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.waitSemaphoreCount(1);
            submitInfo.pWaitSemaphores(stack.longs(imageAvailableSemaphores[currentFrame]));
            submitInfo.pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT));
            submitInfo.pCommandBuffers(stack.pointers(currentCmdBuffer));
            submitInfo.pSignalSemaphores(stack.longs(renderFinishedSemaphores[currentFrame]));

            if (vkQueueSubmit(Display.getGraphicsQueue(), submitInfo, inFlightFences[currentFrame]) != VK_SUCCESS) {
                throw new RuntimeException("Failed to submit draw command buffer!");
            }

            // 5. Present using CURRENT frame's semaphore
            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack);
            presentInfo.sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);
            presentInfo.pWaitSemaphores(stack.longs(renderFinishedSemaphores[currentFrame]));
            presentInfo.swapchainCount(1);
            presentInfo.pSwapchains(stack.longs(swapchain));
            presentInfo.pImageIndices(pImageIndex);

            // 6. Present to screen (ONLY CALLED ONCE)
            result = vkQueuePresentKHR(Display.getPresentQueue(), presentInfo);

            if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                // The OS rejected the frame presentation. Rebuild for the next loop.
                recreateSwapchain();
            } else if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to present swapchain image!");
            }

            // 7. Advance to the next frame (Zero Allocation!)
            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
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
        // 1. Handle Minimization (Pause the engine if width/height is 0)
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

        // 2. Halting the GPU
        // We absolutely CANNOT delete objects that the GPU is currently using to draw a frame.
        vkDeviceWaitIdle(device);

        // 3. Destroy the old architecture
        cleanupSwapchain();

        // 4. Rebuild the architecture with the new dimensions
        createSwapchain();
        createImageViews();
        createRenderPass();

        // Re-compile the shader pipeline so the Viewport & Scissor match the new Extent
        shader.VKShader.EntityShaderPipeline.pipeline = new shader.VKShader.EntityShaderPipeline(device, renderPass, swapchainExtent);

        createFramebuffers();

        // Note: Command Pools and Sync Objects do not depend on window size,
        // so we don't need to recreate them!
    }

    public static void destroy() {
        VkDevice device = Display.getDevice();
        if (device != null) {
            // Wait for GPU to finish its final frame before destroying things
            vkDeviceWaitIdle(device);

            // Destroy Phase 3/4 (Sync & Commands)
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                vkDestroySemaphore(device, renderFinishedSemaphores[i], null);
                vkDestroySemaphore(device, imageAvailableSemaphores[i], null);
                vkDestroyFence(device, inFlightFences[i], null);
            }
            vkDestroyCommandPool(device, commandPool, null);

            // Destroy Phase 1/2 (Swapchain, Pipeline, Framebuffers)
            cleanupSwapchain();
            swapchainExtent.free(); // Free the global struct
        }
    }
}