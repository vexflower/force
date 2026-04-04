package renderer;

import hardware.VulkanContext;
import hardware.Window;
import loader.MeshLoader;
import model.Mesh;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import shader.VKShader;
import util.VK;

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
    private static long[] renderFinishedSemaphores; // Now tied to physical swapchain images
    private static long[] inFlightFences;
    private static long[] imagesInFlight;
    // Near the top of MasterRenderer.java
    private static RenderTarget[] renderTargets = new RenderTarget[4096];

    // --- DEPTH BUFFER ---
    private static long depthImage;
    private static long depthImageMemory;
    private static long depthImageView;
    // Underneath your other static variables...
    private static final float[] FBO_MATRIX_SCRATCH = new float[16];

    public static void setRenderer()
    {
        System.out.println("Initializing Vulkan Master Renderer...");
        createSwapchain();
        createDepthResources();
        createImageViews();
        createRenderPass();
        VKShader.initBindlessHardware(VulkanContext.getDevice());
        VKShader.initUberShader(VulkanContext.getDevice(), renderPass, swapchainExtent, "vertex", "fragment");
        createFramebuffers();
        createCommandPool();
        Mesh.initPrimitives();
        createCommandBuffer();
        createSyncObjects();
        initRenderLoopStructs();
    }

    private static void initRenderLoopStructs()
    {
        Alloc.imageIndex = VK.allocInt(1);
        Alloc.beginInfo = VK.createBeginInfo();
        Alloc.clearValues = VK.createClearValues();
        Alloc.renderArea = VK.createRenderArea();
        Alloc.renderArea.offset().set(0, 0);
        Alloc.renderPassInfo = VK.createRenderPassInfo(renderPass, Alloc.clearValues);
        Alloc.pWaitSemaphores = VK.allocLong(1);
        Alloc.pWaitDstStageMask = VK.allocInt(1).put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
        Alloc.pCommandBuffers = VK.allocPointer(1);
        Alloc.pSignalSemaphores = VK.allocLong(1);
        Alloc.pSwapchains = VK.allocLong(1).put(0, swapchain);
        Alloc.submitInfo = VK.createSubmitInfo(Alloc.pWaitDstStageMask);
        Alloc.presentInfo = VK.createPresentInfo(Alloc.pSwapchains, Alloc.imageIndex);
    }

    private static void createSwapchain()
    {
        try (MemoryStack stack = stackPush())
        {
           VkDevice device = VulkanContext.getDevice();
            VkPhysicalDevice physicalDevice = VulkanContext.getPhysicalDevice();
            long surface = VulkanContext.getSurface();

            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);

            if (capabilities.currentExtent().width() != 0xFFFFFFFF)
            {
                swapchainExtent.width(capabilities.currentExtent().width());
                swapchainExtent.height(capabilities.currentExtent().height());
            }
            else
            {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                GLFW.glfwGetFramebufferSize(Window.getHandle(), width, height);
                swapchainExtent.width(width.get(0));
                swapchainExtent.height(height.get(0));
            }

            IntBuffer pPresentModeCount = stack.mallocInt(1);
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, null);
            IntBuffer pPresentModes = stack.mallocInt(pPresentModeCount.get(0));
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pPresentModeCount, pPresentModes);

            int presentMode = VK_PRESENT_MODE_IMMEDIATE_KHR;
            for (int i = 0; i < pPresentModeCount.get(0); i++) {
                if (pPresentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                    presentMode = VK_PRESENT_MODE_MAILBOX_KHR;
                    System.out.println("Triple Buffering (Mailbox) Enabled!");
                    break;
                }
            }

            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                capabilities.maxImageCount();
            }

            swapchainImageFormat = VK_FORMAT_B8G8R8A8_UNORM;

            VkSwapchainCreateInfoKHR createInfo = VK.createCreateInfo(
                    stack,
                    surface,
                    swapchainImageFormat,
                    swapchainExtent,
                    presentMode,
                    capabilities,
                    imageCount
            );

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
       VkDevice device = VulkanContext.getDevice();
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
           VkDevice device = VulkanContext.getDevice();

            VkAttachmentDescription.Buffer colorAttachment = VK.createColorAttachmentBuffer(stack, swapchainImageFormat);

            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
            VK.modifyBufferAttachments(attachments, swapchainImageFormat);

            VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack);
            colorAttachmentRef.attachment(0);
            colorAttachmentRef.layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            // [NEW] Reference the Depth attachment
            VkAttachmentReference depthAttachmentRef = VkAttachmentReference.calloc(stack);
            depthAttachmentRef.attachment(1).layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS);
            subpass.colorAttachmentCount(1);
            subpass.pColorAttachments(colorAttachmentRef);
            subpass.pDepthStencilAttachment(depthAttachmentRef);

            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
            VK.modifyDependencies(stack, dependencies);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack);
            renderPassInfo.sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);
            renderPassInfo.pAttachments(attachments);
            renderPassInfo.pSubpasses(subpass);
            renderPassInfo.pDependencies(dependencies); // <--- Make sure this points to the new 'dependencies' variable!
            LongBuffer pRenderPass = stack.mallocLong(1);
            if (vkCreateRenderPass(device, renderPassInfo, null, pRenderPass) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan Render Pass!");
            }
            renderPass = pRenderPass.get(0);
        }
    }

    private static int findMemoryType(int typeFilter, int properties) {
        return VK.findMemoryType(typeFilter, properties);
    }

    private static void createDepthResources() {
        try (MemoryStack stack = stackPush()) {
           VkDevice device = VulkanContext.getDevice();
            int format = VK_FORMAT_D32_SFLOAT; // Standard high-precision depth format

            // 1. Create the Image
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .extent(VkExtent3D.calloc(stack).set(swapchainExtent.width(), swapchainExtent.height(), 1))
                    .mipLevels(1).arrayLayers(1).format(format).tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK_SAMPLE_COUNT_1_BIT);

            LongBuffer pImage = stack.mallocLong(1);
            if (vkCreateImage(device, imageInfo, null, pImage) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create depth image!");
            }
            depthImage = pImage.get(0);

            // 2. Allocate GPU Memory
            VkMemoryRequirements memRequirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, depthImage, memRequirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memRequirements.size())
                    .memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            LongBuffer pMemory = stack.mallocLong(1);
            if (vkAllocateMemory(device, allocInfo, null, pMemory) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate depth image memory!");
            }
            depthImageMemory = pMemory.get(0);
            vkBindImageMemory(device, depthImage, depthImageMemory, 0);

            // 3. Create the Image View
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(depthImage).viewType(VK_IMAGE_VIEW_TYPE_2D).format(format)
                    .subresourceRange(r -> r.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1));

            LongBuffer pView = stack.mallocLong(1);
            if (vkCreateImageView(device, viewInfo, null, pView) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create depth image view!");
            }
            depthImageView = pView.get(0);
        }
    }

    private static void createFramebuffers() {
       VkDevice device = VulkanContext.getDevice();
        framebuffers = new long[swapchainImageViews.length];

        try (MemoryStack stack = stackPush()) {
            LongBuffer pFramebuffer = stack.mallocLong(1);
            LongBuffer attachments = stack.mallocLong(2);

            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack);
            framebufferInfo.sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);
            framebufferInfo.renderPass(renderPass);
            framebufferInfo.width(swapchainExtent.width());
            framebufferInfo.height(swapchainExtent.height());
            framebufferInfo.layers(1);

            for (int i = 0; i < swapchainImageViews.length; i++) {
                attachments.put(0, swapchainImageViews[i]);
                attachments.put(1, depthImageView); // [NEW] Bind the depth image!
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
            poolInfo.queueFamilyIndex(VulkanContext.getGraphicsQueueFamilyIndex());
            poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);
            if (vkCreateCommandPool(VulkanContext.getDevice(), poolInfo, null, pCommandPool) != VK_SUCCESS) {
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
            if (vkAllocateCommandBuffers(VulkanContext.getDevice(), allocInfo, pCommandBuffers) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers!");
            }

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), VulkanContext.getDevice());
            }
        }
    }

    private static void createSyncObjects() {
        try (MemoryStack stack = stackPush()) {
            imageAvailableSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
            inFlightFences = new long[MAX_FRAMES_IN_FLIGHT];

            // Explicitly sizing the tracking arrays to the swapchain length
            imagesInFlight = new long[swapchainImages.length];
            renderFinishedSemaphores = new long[swapchainImages.length];

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack);
            fenceInfo.sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            fenceInfo.flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                if (vkCreateSemaphore(VulkanContext.getDevice(), semaphoreInfo, null, pSemaphore) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create semaphore!");
                }
                imageAvailableSemaphores[i] = pSemaphore.get(0);

                if (vkCreateFence(VulkanContext.getDevice(), fenceInfo, null, pFence) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create fence!");
                }
                inFlightFences[i] = pFence.get(0);
            }

            for (int i = 0; i < swapchainImages.length; i++) {
                if (vkCreateSemaphore(VulkanContext.getDevice(), semaphoreInfo, null, pSemaphore) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create render finished semaphore!");
                }
                renderFinishedSemaphores[i] = pSemaphore.get(0);
            }
        }
    }

    public static void render(RenderState state) {
        VkDevice device = VulkanContext.getDevice();
        vkWaitForFences(device, inFlightFences[currentFrame], true, -1);

        int acquireResult = vkAcquireNextImageKHR(device, swapchain, -1, imageAvailableSemaphores[currentFrame], VK_NULL_HANDLE, Alloc.imageIndex);
        if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
            recreateSwapchain();
            return;
        }

        int imageIndex = Alloc.imageIndex.get(0);
        if (imagesInFlight[imageIndex] != VK_NULL_HANDLE) {
            vkWaitForFences(device, imagesInFlight[imageIndex], true, -1);
        }

        imagesInFlight[imageIndex] = inFlightFences[currentFrame];

        vkResetFences(device, inFlightFences[currentFrame]);
        vkResetCommandBuffer(commandBuffers[currentFrame], 0);

        if (vkBeginCommandBuffer(commandBuffers[currentFrame], Alloc.beginInfo) != VK_SUCCESS) {
            throw new RuntimeException("Failed to begin recording command buffer!");
        }

        // ========================================================================
        // PASS 1: RENDER ALL OFF-SCREEN FBOs
        // ========================================================================
        for (int i = 0; i < state.snapshotCount; i++) {
            SceneSnapshot snap = state.snapshots[i];

            // ---> THE FIX: Use pure data flags to resolve the FBO automatically!
            if (snap.isOffscreen) {
                // Lazy-init the Target if the UI created a new Panel!
                if (renderTargets[snap.containerId] == null) {
                    renderTargets[snap.containerId] = new RenderTarget(snap.width, snap.height);
                    renderTargets[snap.containerId].init();
                }

                RenderTarget target = renderTargets[snap.containerId];

                // Dynamic FBO Resize Check!
                if (target.width != snap.width || target.height != snap.height) {
                    target.destroy();
                    target.width = snap.width;
                    target.height = snap.height;
                    target.init();
                }

                target.bind(commandBuffers[currentFrame], snap.bgR, snap.bgG, snap.bgB, snap.bgA);
                VKShader.bindUbershader(commandBuffers[currentFrame]);
                drawSnapshotEntities(commandBuffers[currentFrame], snap);
                target.unbind(commandBuffers[currentFrame]);
            }
        }

        // ========================================================================
        // PASS 2: MAIN SWAPCHAIN PASS (Root 3D Entities)
        // ========================================================================
        Alloc.swapchainPass(imageIndex);

        vkCmdBeginRenderPass(commandBuffers[currentFrame], Alloc.renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        VKShader.bindUbershader(commandBuffers[currentFrame]);

        try (MemoryStack stack = stackPush()) {
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0.0f).y(0.0f)
                    .width(swapchainExtent.width()).height(swapchainExtent.height())
                    .minDepth(0.0f).maxDepth(1.0f);
            vkCmdSetViewport(commandBuffers[currentFrame], 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack)
                    .offset(VkOffset2D.calloc(stack).set(0, 0))
                    .extent(swapchainExtent);
            vkCmdSetScissor(commandBuffers[currentFrame], 0, scissor);

            // Find the Root Scene snapshot (the one that is NOT offscreen) and draw it!
            for (int i = 0; i < state.snapshotCount; i++) {
                if (!state.snapshots[i].isOffscreen) {
                    drawSnapshotEntities(commandBuffers[currentFrame], state.snapshots[i]);
                    break;
                }
            }

            // ========================================================================
            // PASS 3: STACKED UI PANELS
            // ========================================================================
            if (state.uiElementCount > 0) {
                int squareMeshId = Mesh.SQUARE.vaoId;
                LongBuffer vertexBuffers = stack.longs(MeshLoader.getVertexBuffer(squareMeshId), MeshLoader.getUvBuffer(squareMeshId));
                vkCmdBindVertexBuffers(commandBuffers[currentFrame], 0, vertexBuffers, stack.longs(0, 0));
                vkCmdBindIndexBuffer(commandBuffers[currentFrame], MeshLoader.getIndexBuffer(squareMeshId), 0, VK_INDEX_TYPE_UINT32);

                for (int i = 0; i < state.uiElementCount; i++) {
                    int containerId = state.uiTextureIds[i];
                    // Automatically grab the texture generated in Pass 1!
                    int actualTexId = renderTargets[containerId] != null ? renderTargets[containerId].textureId : 0;

                    VKShader.pushUIState(commandBuffers[currentFrame], state.uiTransforms, i * 16, actualTexId, (float)hardware.Window.getWidth(), (float)hardware.Window.getHeight());
                    vkCmdDrawIndexed(commandBuffers[currentFrame], MeshLoader.getIndexCount(squareMeshId), 1, 0, 0, 0);
                }
            }
        }

        vkCmdEndRenderPass(commandBuffers[currentFrame]);

        if (vkEndCommandBuffer(commandBuffers[currentFrame]) != VK_SUCCESS) {
            throw new RuntimeException("Failed to record command buffer!");
        }

        Alloc.submitAndPresent(imageIndex);

        int submitResult = vkQueueSubmit(VulkanContext.getGraphicsQueue(), Alloc.submitInfo, inFlightFences[currentFrame]);
        if (submitResult != VK_SUCCESS) {
            throw new RuntimeException("GPU CRASH! vkQueueSubmit returned Vulkan Error Code: " + submitResult);
        }

        int presentResult = Alloc.queuePresentInfo();

        if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR || hardware.Window.wasResized) {
            hardware.Window.wasResized = false;
            recreateSwapchain();
        } else if (presentResult != VK_SUCCESS) {
            throw new RuntimeException("Failed to present swapchain image!");
        }

        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
    }

    private static void drawSnapshotEntities(VkCommandBuffer cmd, SceneSnapshot snap) {
        try (MemoryStack stack = stackPush()) {
            int lastMeshId = -1; // Track the last bound mesh

            for (int i = 0; i < snap.entityCount; i++) {
                int meshId = snap.meshIds[i];
                int texId = snap.textureIds[i];

                // ONLY bind the buffers if we switched to a different 3D Model!
                if (meshId != lastMeshId) {
                    LongBuffer vertexBuffers = stack.longs(MeshLoader.getVertexBuffer(meshId), MeshLoader.getUvBuffer(meshId));
                    vkCmdBindVertexBuffers(cmd, 0, vertexBuffers, stack.longs(0, 0));
                    vkCmdBindIndexBuffer(cmd, MeshLoader.getIndexBuffer(meshId), 0, VK_INDEX_TYPE_UINT32);
                    lastMeshId = meshId;
                }

                VKShader.pushEntityState(cmd, snap.transforms, i * 16, texId);
                vkCmdDrawIndexed(cmd, MeshLoader.getIndexCount(meshId), 1, 0, 0, 0);
            }
        }
    }

    private static void cleanupSwapchain() {
       VkDevice device = VulkanContext.getDevice();


        for (long framebuffer : framebuffers) {
            vkDestroyFramebuffer(device, framebuffer, null);
        }

        if (VKShader.ubershaderPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, VKShader.ubershaderPipeline, null);
            vkDestroyPipelineLayout(device, VKShader.pipelineLayout, null);
            VKShader.ubershaderPipeline = VK_NULL_HANDLE;
            VKShader.pipelineLayout = VK_NULL_HANDLE;
        }

        if (renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, renderPass, null);
        }

        if (depthImageView != VK_NULL_HANDLE)
            vkDestroyImageView(device, depthImageView, null);

        if (depthImage != VK_NULL_HANDLE)
            vkDestroyImage(device, depthImage, null);

        if (depthImageMemory != VK_NULL_HANDLE)
            vkFreeMemory(device, depthImageMemory, null);

        for (long imageView : swapchainImageViews) {
            vkDestroyImageView(device, imageView, null);
        }

        // Properly clear out the GPU-bound semaphores
        if (renderFinishedSemaphores != null) {
            for (long sem : renderFinishedSemaphores) {
                if (sem != VK_NULL_HANDLE) {
                    vkDestroySemaphore(device, sem, null);
                }
            }
            renderFinishedSemaphores = null;
        }

        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, null);
            swapchain = VK_NULL_HANDLE;
        }
    }

    private static void recreateSwapchain() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);

            GLFW.glfwGetFramebufferSize(Window.getHandle(), width, height);
            while (width.get(0) == 0 || height.get(0) == 0) {
                GLFW.glfwGetFramebufferSize(Window.getHandle(), width, height);
                GLFW.glfwWaitEvents();
            }
        }

       VkDevice device = VulkanContext.getDevice();
        vkDeviceWaitIdle(device);
        cleanupSwapchain();

        createSwapchain();
        createDepthResources();
        createImageViews();

        // 1. Generate the fresh Render Pass FIRST
        createRenderPass();

        // 2. THEN compile the shader using the new valid handle!
        VKShader.initUberShader(VulkanContext.getDevice(), renderPass, swapchainExtent, "vertex", "fragment");

        imagesInFlight = new long[swapchainImages.length];

        // Rebuild the renderFinished array to exactly match the new swapchain allocation
        renderFinishedSemaphores = new long[swapchainImages.length];
        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack);
            semaphoreInfo.sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            LongBuffer pSemaphore = stack.mallocLong(1);
            for (int i = 0; i < swapchainImages.length; i++) {
                if (vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create render finished semaphore!");
                }
                renderFinishedSemaphores[i] = pSemaphore.get(0);
            }
        }

        createFramebuffers();

        Alloc.checkRenderPassInfo();
    }

    public static void destroy() {
       VkDevice device = VulkanContext.getDevice();
        if (device != null) {
            vkDeviceWaitIdle(device);

            // --- ADD THIS BLOCK ---
            for (int i = 0; i < renderTargets.length; i++) {
                if (renderTargets[i] != null) {
                    renderTargets[i].destroy();
                    renderTargets[i] = null;
                }
            }

            if (VKShader.bindlessDescriptorPool != VK_NULL_HANDLE) {
                vkDestroyDescriptorPool(device, VKShader.bindlessDescriptorPool, null);
                vkDestroyDescriptorSetLayout(device, VKShader.bindlessDescriptorSetLayout, null);
            }

            // Do not destroy renderFinishedSemaphores here; cleanupSwapchain() handles it.
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                vkDestroySemaphore(device, imageAvailableSemaphores[i], null);
                vkDestroyFence(device, inFlightFences[i], null);
            }
            vkDestroyCommandPool(device, commandPool, null);

            cleanupSwapchain();
            swapchainExtent.free();
        }

        Alloc.freeAll();
    }

    public static int getSwapchainImageFormat()
    {
        return swapchainImageFormat;
    }
    public static long getCommandPool() {
        return commandPool;
    }

    public static class Alloc
    {
        // Zero-GC Cache
        private static IntBuffer imageIndex;
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

        public static void freeAll()
        {
            if(beginInfo != null) {
                beginInfo.free();
                clearValues.free();
                renderArea.free();
                renderPassInfo.free();
                submitInfo.free();
                presentInfo.free();

                MemoryUtil.memFree(imageIndex);
                MemoryUtil.memFree(pWaitSemaphores);
                MemoryUtil.memFree(pWaitDstStageMask);
                MemoryUtil.memFree(pCommandBuffers);
                MemoryUtil.memFree(pSignalSemaphores);
                MemoryUtil.memFree(pSwapchains);
            }
        }

        public static void swapchainPass(int index)
        {
            renderArea.offset().set(0, 0);
            renderArea.extent(swapchainExtent);
            renderPassInfo.renderArea(renderArea);
            renderPassInfo.pClearValues(clearValues);
            renderPassInfo.framebuffer(framebuffers[index]);
        }

        public static void submitAndPresent(int index)
        {
            submitInfo.pWaitSemaphores(pWaitSemaphores.put(0, imageAvailableSemaphores[currentFrame]));
            submitInfo.pCommandBuffers(pCommandBuffers.put(0, commandBuffers[currentFrame]));
            submitInfo.pSignalSemaphores(pSignalSemaphores.put(0, renderFinishedSemaphores[index]));
        }

        public static int queuePresentInfo()
        {
            presentInfo.pWaitSemaphores(pSignalSemaphores);
            presentInfo.pImageIndices(imageIndex);
            return vkQueuePresentKHR(VulkanContext.getPresentQueue(), presentInfo);
        }

        public static void checkRenderPassInfo()
        {
            if (renderPassInfo != null) {
                renderPassInfo.renderPass(renderPass);
                pSwapchains.put(0, swapchain);
            }
        }
    }
}