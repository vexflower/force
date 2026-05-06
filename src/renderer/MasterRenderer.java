package renderer;

import hardware.VulkanContext;
import hardware.Window;
import loader.GeomRegistry;
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

    // --- HARDWARE LIMITS & STRIDES (SIMD Style) ---
    public static final int MAX_FRAMES_IN_FLIGHT    = 3;
    public static final int MAX_ENTITIES_PER_FRAME  = 100_000;
    public static final int MAX_COMMANDS_PER_FRAME  = 10_000_000;
    public static final int MAX_MESHLETS_PER_SCENE  = 1_000_000;

    private static final long STRIDE_ENTITY   = 96L;
    private static final long STRIDE_INDIRECT = 20L;
    private static final long STRIDE_COUNTER  = 4L; // 4 bytes per snapshot

    // --- PRE-CALCULATED CAPACITIES ---
    private static final long SIZE_ENTITY_FRAME   = MAX_ENTITIES_PER_FRAME * STRIDE_ENTITY;
    public  static final long SIZE_ENTITY_TOTAL   = SIZE_ENTITY_FRAME * MAX_FRAMES_IN_FLIGHT;

    private static final long SIZE_INDIRECT_FRAME = MAX_COMMANDS_PER_FRAME * STRIDE_INDIRECT;
    private static final long SIZE_INDIRECT_TOTAL = SIZE_INDIRECT_FRAME * MAX_FRAMES_IN_FLIGHT;

    private static final long SIZE_COUNTER_FRAME  = 20L * STRIDE_COUNTER; // 10 Snapshots
    private static final long SIZE_COUNTER_TOTAL  = SIZE_COUNTER_FRAME * MAX_FRAMES_IN_FLIGHT;

    private static long swapchain;
    private static long[] swapchainImages;
    private static long[] swapchainImageViews;
    private static int swapchainImageFormat;
    private static VkExtent2D swapchainExtent = VkExtent2D.calloc();

    private static long renderPass;
    private static long[] framebuffers;
    private static int currentFrame = 0;

    private static long commandPool;
    private static VkCommandBuffer[] commandBuffers;

    private static long[] imageAvailableSemaphores;
    private static long[] renderFinishedSemaphores;
    private static long[] inFlightFences;
    private static long[] imagesInFlight;
    private static RenderTarget[] renderTargets = new RenderTarget[4096];

    private static long depthImage, depthImageMemory, depthImageView;

    private static long globalEntityBuffer, globalEntityMemory, mappedEntityPointer;
    private static long atomicCounterBuffer, atomicCounterMemory;
    private static long globalIndirectBuffer, globalIndirectMemory;
    // --- HI-Z OCCLUSION CULLING ---
    private static long hizImage, hizImageMemory, hizImageView;
    private static int hizMipLevels;
    private static long hizSampler;

    // NEW: Depth Pre-Pass Infrastructure
    private static long hizRenderPass;
    private static long hizFramebuffer;
    public static long hizPipelineLayout;
    public static long hizPipeline;
    public static long[] hizMipViews; // NEW
    // --- HI-Z OCCLUSION CULLING ---
    private static long prepassDepthImage, prepassDepthMemory, prepassDepthView; // NEW: The actual depth buffer

    public static void setRenderer() {
        System.out.println("Initializing Vulkan Master Renderer (Data-Oriented)...");

        createSwapchain();
        createDepthResources();

        // 1. MUST INITIALIZE BINDLESS FIRST!
        VKShader.initBindlessHardware(VulkanContext.getDevice());

        // 2. Now we can safely create and map the Hi-Z pyramid
        createHiZResources();

        createImageViews();
        createRenderPass();
        createHiZRenderPass();
        createFramebuffers();
        createHiZFramebuffer();
        createCommandPool();
        createCommandBuffer();

        // 3. Compile the Pipelines
        initShaders();
        initMemoryBanks();
        createSyncObjects();
        initRenderLoopStructs();
    }

    private static void initHardwareSurfaces() {
        createSwapchain();
        createDepthResources();
        createHiZResources();
        createImageViews();
        createRenderPass();
        createFramebuffers();
        createCommandPool();
        createCommandBuffer();
    }

    private static void initShaders() {
        VkDevice device = VulkanContext.getDevice();
        VKShader.initUberShader(device, renderPass, swapchainExtent, "vertex", "fragment");
        VKShader.initDepthPipeline(device, hizRenderPass, swapchainExtent, "vertex");
        VKShader.initComputeShader(device);
        VKShader.initHiZCompute(device);
    }

    private static void initMemoryBanks() {
        VkDevice device = VulkanContext.getDevice();

        // 1. Entity Buffer (Host Visible - CPU maps data here directly)
        globalEntityBuffer = VK.createBuffer(SIZE_ENTITY_TOTAL, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
        globalEntityMemory = VK.allocateBufferMemory(globalEntityBuffer, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        try (MemoryStack stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int result = vkMapMemory(device, globalEntityMemory, 0, SIZE_ENTITY_TOTAL, 0, pData);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to map Entity Vault!");
            mappedEntityPointer = pData.get(0);
        }

        // 2. Indirect Buffer (Device Local - GPU reads/writes this internally)
        globalIndirectBuffer = VK.createBuffer(SIZE_INDIRECT_TOTAL, VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        globalIndirectMemory = VK.allocateBufferMemory(globalIndirectBuffer, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        // 3. Atomic Counters (Device Local - GPU increments this)
        atomicCounterBuffer = VK.createBuffer(SIZE_COUNTER_TOTAL, VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        atomicCounterMemory = VK.allocateBufferMemory(atomicCounterBuffer, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
    }

    private static void initRenderLoopStructs() {
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

    private static void createHiZResources() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = VulkanContext.getDevice();

            // 1. Create the PRE-PASS DEPTH BUFFER (D32_SFLOAT)
            VkImageCreateInfo depthInfo = VK.createImageInfo(stack, swapchainExtent.width(), swapchainExtent.height(), VK_FORMAT_D32_SFLOAT, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT);
            LongBuffer pDepthImage = stack.mallocLong(1);
            if (vkCreateImage(device, depthInfo, null, pDepthImage) != VK_SUCCESS) throw new RuntimeException("Failed to create Depth Image");
            prepassDepthImage = pDepthImage.get(0);

            VkMemoryRequirements memReqs1 = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, prepassDepthImage, memReqs1);

            VkMemoryAllocateInfo allocInfo1 = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memReqs1.size())
                    .memoryTypeIndex(VK.findMemoryType(memReqs1.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            LongBuffer pDepthMem = stack.mallocLong(1);
            if (vkAllocateMemory(device, allocInfo1, null, pDepthMem) != VK_SUCCESS) throw new RuntimeException("Failed to alloc Depth Memory");
            prepassDepthMemory = pDepthMem.get(0);
            if (vkBindImageMemory(device, prepassDepthImage, prepassDepthMemory, 0) != VK_SUCCESS) throw new RuntimeException("Failed to bind Depth Memory");

            VkImageViewCreateInfo depthViewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(prepassDepthImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_D32_SFLOAT);
            // Bypassing the lambda closure completely!
            depthViewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT);
            depthViewInfo.subresourceRange().baseMipLevel(0);
            depthViewInfo.subresourceRange().levelCount(1);
            depthViewInfo.subresourceRange().baseArrayLayer(0);
            depthViewInfo.subresourceRange().layerCount(1);

            LongBuffer pDepthView = stack.mallocLong(1);
            if (vkCreateImageView(device, depthViewInfo, null, pDepthView) != VK_SUCCESS) throw new RuntimeException("Failed to create Depth View");
            prepassDepthView = pDepthView.get(0);

            // 2. Create the Hi-Z PYRAMID (R32_SFLOAT, COLOR_BIT)
            hizMipLevels = (int) Math.floor(Math.log(Math.max(swapchainExtent.width(), swapchainExtent.height())) / Math.log(2.0)) + 1;

            VkImageCreateInfo hizInfo = VK.createImageInfo(stack, swapchainExtent.width(), swapchainExtent.height(), VK_FORMAT_R32_SFLOAT, VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT);
            hizInfo.mipLevels(hizMipLevels);

            LongBuffer pHiZ = stack.mallocLong(1);
            if (vkCreateImage(device, hizInfo, null, pHiZ) != VK_SUCCESS) throw new RuntimeException("Failed to create Hi-Z Image");
            hizImage = pHiZ.get(0);

            VkMemoryRequirements memReqs2 = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, hizImage, memReqs2);

            VkMemoryAllocateInfo allocInfo2 = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memReqs2.size())
                    .memoryTypeIndex(VK.findMemoryType(memReqs2.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            LongBuffer pHiZMem = stack.mallocLong(1);
            if (vkAllocateMemory(device, allocInfo2, null, pHiZMem) != VK_SUCCESS) throw new RuntimeException("Failed to alloc Hi-Z Memory");
            hizImageMemory = pHiZMem.get(0);
            if (vkBindImageMemory(device, hizImage, hizImageMemory, 0) != VK_SUCCESS) throw new RuntimeException("Failed to bind Hi-Z Memory");

            VkImageViewCreateInfo hizViewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(hizImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_R32_SFLOAT);
            hizViewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
            hizViewInfo.subresourceRange().baseMipLevel(0);
            hizViewInfo.subresourceRange().levelCount(hizMipLevels);
            hizViewInfo.subresourceRange().baseArrayLayer(0);
            hizViewInfo.subresourceRange().layerCount(1);

            LongBuffer pHiZView = stack.mallocLong(1);
            if (vkCreateImageView(device, hizViewInfo, null, pHiZView) != VK_SUCCESS) throw new RuntimeException("Failed to create Hi-Z View");
            hizImageView = pHiZView.get(0);

            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_NEAREST)
                    .minFilter(VK_FILTER_NEAREST)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .minLod(0.0f).maxLod((float) hizMipLevels);

            LongBuffer pSampler = stack.mallocLong(1);
            if (vkCreateSampler(device, samplerInfo, null, pSampler) != VK_SUCCESS) throw new RuntimeException("Failed to create Hi-Z Sampler");
            hizSampler = pSampler.get(0);

            hizMipViews = new long[hizMipLevels];
            for (int i = 0; i < hizMipLevels; i++) {
                VkImageViewCreateInfo mipViewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(hizImage)
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(VK_FORMAT_R32_SFLOAT);

                mipViewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
                mipViewInfo.subresourceRange().baseMipLevel(i);
                mipViewInfo.subresourceRange().levelCount(1);
                mipViewInfo.subresourceRange().baseArrayLayer(0);
                mipViewInfo.subresourceRange().layerCount(1);

                LongBuffer pMipView = stack.mallocLong(1);
                if (vkCreateImageView(device, mipViewInfo, null, pMipView) != VK_SUCCESS) throw new RuntimeException("Failed to create Mip View " + i);
                hizMipViews[i] = pMipView.get(0);

                VKShader.updateHiZMipDescriptor(i, hizMipViews[i], hizSampler);
            }

            VKShader.updatePrepassDepthDescriptor(prepassDepthView, hizSampler);
        }
    }

    private static void createHiZRenderPass() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = VulkanContext.getDevice();
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(1, stack)
                    .format(VK_FORMAT_D32_SFLOAT)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL); // Optimized for Compute reading!

            VkAttachmentReference.Buffer depthRef = VkAttachmentReference.calloc(1, stack)
                    .attachment(0).layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .pDepthStencilAttachment(depthRef.get(0));

            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
            dependencies.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT).dstStageMask(VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT).dstAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
            dependencies.get(1)
                    .srcSubpass(0).dstSubpass(VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT).dstStageMask(VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO).pAttachments(attachments).pSubpasses(subpass).pDependencies(dependencies);

            LongBuffer pRenderPass = stack.mallocLong(1);
            vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
            hizRenderPass = pRenderPass.get(0);
        }
    }

    private static void createHiZFramebuffer() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = VulkanContext.getDevice();
            LongBuffer attachments = stack.mallocLong(1).put(0, prepassDepthView); // Attach the real depth buffer!
            VkFramebufferCreateInfo fboInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO).renderPass(hizRenderPass).pAttachments(attachments)
                    .width(swapchainExtent.width()).height(swapchainExtent.height()).layers(1);

            LongBuffer pFramebuffer = stack.mallocLong(1);
            vkCreateFramebuffer(device, fboInfo, null, pFramebuffer);
            hizFramebuffer = pFramebuffer.get(0);
        }
    }

    private static void createSwapchain() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = VulkanContext.getDevice();
            VkPhysicalDevice physicalDevice = VulkanContext.getPhysicalDevice();
            long surface = VulkanContext.getSurface();

            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);

            if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
                swapchainExtent.width(capabilities.currentExtent().width());
                swapchainExtent.height(capabilities.currentExtent().height());
            } else {
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
                    break;
                }
            }

            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                capabilities.maxImageCount();
            }

            swapchainImageFormat = VK_FORMAT_B8G8R8A8_UNORM;

            VkSwapchainCreateInfoKHR createInfo = VK.createCreateInfo(stack, surface, swapchainImageFormat, swapchainExtent, presentMode, capabilities, imageCount);

            LongBuffer pSwapchain = stack.mallocLong(1);
            vkCreateSwapchainKHR(device, createInfo, null, pSwapchain);
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
                VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                        .image(swapchainImages[i])
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(swapchainImageFormat);

                createInfo.components()
                        .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                        .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                        .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                        .a(VK_COMPONENT_SWIZZLE_IDENTITY);

                createInfo.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);

                LongBuffer pImageView = stack.mallocLong(1);
                vkCreateImageView(device, createInfo, null, pImageView);
                swapchainImageViews[i] = pImageView.get(0);
            }
        }
    }

    private static void createRenderPass() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = VulkanContext.getDevice();

            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(2, stack);
            VK.modifyBufferAttachments(attachments, swapchainImageFormat);

            VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            VkAttachmentReference depthAttachmentRef = VkAttachmentReference.calloc(stack)
                    .attachment(1)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(1)
                    .pColorAttachments(colorAttachmentRef)
                    .pDepthStencilAttachment(depthAttachmentRef);

            VkSubpassDependency.Buffer dependencies = VkSubpassDependency.calloc(2, stack);
            VK.modifyDependencies(stack, dependencies);

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(dependencies);

            LongBuffer pRenderPass = stack.mallocLong(1);
            vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
            renderPass = pRenderPass.get(0);
        }
    }

    private static void createDepthResources() {
        try (MemoryStack stack = stackPush()) {
            VkDevice device = VulkanContext.getDevice();
            int format = VK_FORMAT_D32_SFLOAT;

            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .extent(VkExtent3D.calloc(stack).set(swapchainExtent.width(), swapchainExtent.height(), 1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .format(format)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK_SAMPLE_COUNT_1_BIT);

            LongBuffer pImage = stack.mallocLong(1);
            vkCreateImage(device, imageInfo, null, pImage);
            depthImage = pImage.get(0);

            VkMemoryRequirements memRequirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, depthImage, memRequirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memRequirements.size())
                    .memoryTypeIndex(VK.findMemoryType(memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            LongBuffer pMemory = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMemory);
            depthImageMemory = pMemory.get(0);

            vkBindImageMemory(device, depthImage, depthImageMemory, 0);

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(depthImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(format)
                    .subresourceRange(r -> r
                            .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    );

            LongBuffer pView = stack.mallocLong(1);
            vkCreateImageView(device, viewInfo, null, pView);
            depthImageView = pView.get(0);
        }
    }

    private static void createFramebuffers() {
        VkDevice device = VulkanContext.getDevice();
        framebuffers = new long[swapchainImageViews.length];

        try (MemoryStack stack = stackPush()) {
            LongBuffer pFramebuffer = stack.mallocLong(1);
            LongBuffer attachments = stack.mallocLong(2);

            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPass)
                    .width(swapchainExtent.width())
                    .height(swapchainExtent.height())
                    .layers(1);

            for (int i = 0; i < swapchainImageViews.length; i++) {
                attachments.put(0, swapchainImageViews[i]);
                attachments.put(1, depthImageView);
                framebufferInfo.pAttachments(attachments);

                vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer);
                framebuffers[i] = pFramebuffer.get(0);
            }
        }
    }

    private static void createCommandPool() {
        try (MemoryStack stack = stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                    .queueFamilyIndex(VulkanContext.getGraphicsQueueFamilyIndex())
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);
            vkCreateCommandPool(VulkanContext.getDevice(), poolInfo, null, pCommandPool);
            commandPool = pCommandPool.get(0);
        }
    }

    private static void createCommandBuffer() {
        commandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];

        try (MemoryStack stack = stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(MAX_FRAMES_IN_FLIGHT);

            PointerBuffer pCommandBuffers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
            vkAllocateCommandBuffers(VulkanContext.getDevice(), allocInfo, pCommandBuffers);

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), VulkanContext.getDevice());
            }
        }
    }

    private static void createSyncObjects() {
        try (MemoryStack stack = stackPush()) {
            imageAvailableSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
            inFlightFences = new long[MAX_FRAMES_IN_FLIGHT];
            imagesInFlight = new long[swapchainImages.length];
            renderFinishedSemaphores = new long[swapchainImages.length];

            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                vkCreateSemaphore(VulkanContext.getDevice(), semaphoreInfo, null, pSemaphore);
                imageAvailableSemaphores[i] = pSemaphore.get(0);

                vkCreateFence(VulkanContext.getDevice(), fenceInfo, null, pFence);
                inFlightFences[i] = pFence.get(0);
            }

            for (int i = 0; i < swapchainImages.length; i++) {
                vkCreateSemaphore(VulkanContext.getDevice(), semaphoreInfo, null, pSemaphore);
                renderFinishedSemaphores[i] = pSemaphore.get(0);
            }
        }
    }

    public static void linkVaults() {
        VKShader.bindSSBOs(
                globalEntityBuffer,
                GeomRegistry.gpuPos, GeomRegistry.gpuUv, GeomRegistry.gpuNorm, GeomRegistry.gpuTang,
                GeomRegistry.gpuMBounds, GeomRegistry.gpuMData,
                GeomRegistry.gpuMCones,
                globalIndirectBuffer, atomicCounterBuffer
        );
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
        VkCommandBuffer cmd = commandBuffers[currentFrame];
        vkResetCommandBuffer(cmd, 0);
        vkBeginCommandBuffer(cmd, Alloc.beginInfo);

        // --- PRE-CALCULATED OFFSETS FOR ZERO-LATENCY DISPATCH ---
        long frameEntityOffset   = currentFrame * SIZE_ENTITY_FRAME;
        long frameIndirectOffset = currentFrame * SIZE_INDIRECT_FRAME;
        long frameCounterOffset  = currentFrame * SIZE_COUNTER_FRAME;

        // ========================================================================
        // PHASE 4.1: THE RESET
        // ========================================================================

        vkCmdFillBuffer(cmd, atomicCounterBuffer, frameCounterOffset, SIZE_COUNTER_FRAME, 0);
        vkCmdFillBuffer(cmd, globalIndirectBuffer, frameIndirectOffset, SIZE_INDIRECT_FRAME, 0);

        try (MemoryStack stack = stackPush()) {
            VkMemoryBarrier.Buffer clearBarrier = VkMemoryBarrier.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT);

            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, clearBarrier, null, null);
        }

        // ========================================================================
        // PHASE 4.2: COMPUTE PASS 1 (CULL THE WALLS ONLY)
        // ========================================================================
        for (int i = 0; i < state.snapshotCount; i++) {
            SceneSnapshot snap = state.snapshots[i];
            if (snap.entityCount > 0) {
                long snapOffset = snap.globalEntityOffset * STRIDE_ENTITY;
                MemoryUtil.memCopy(snap.entityData.address(), mappedEntityPointer + frameEntityOffset + snapOffset, snap.entityCount * 24L * 4L);

                VKShader.bindComputeShader(cmd);
                int trueInstanceOffset = (currentFrame * MAX_ENTITIES_PER_FRAME) + snap.globalEntityOffset;
                int commandOffset = (currentFrame * MAX_COMMANDS_PER_FRAME) + (i * MAX_MESHLETS_PER_SCENE);
                int globalSnapshotIndex = (currentFrame * 10) + i;

                // Notice the 0.0f at the end for passType!
                VKShader.pushComputeData(cmd, snap.vpMatrix.address(), snap.camX, snap.camY, snap.camZ, snap.p11, (float)swapchainExtent.width(), (float)swapchainExtent.height(), (float)hizMipLevels, trueInstanceOffset, commandOffset, globalSnapshotIndex, 0.0f);
                vkCmdDispatch(cmd, 16, snap.entityCount, 1);
            }
        }

        try (MemoryStack stack = stackPush()) {
            VkMemoryBarrier.Buffer computeBarrier = VkMemoryBarrier.calloc(1, stack).sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER).srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT, 0, computeBarrier, null, null);
        }

        // ========================================================================
        // PHASE 4.2.5: PRE-PASS & HI-Z (DRAW THE WALLS ONLY)
        // ========================================================================
        SceneSnapshot mainSnap = null;
        int mainSnapIndex = 0;
        for (int i = 0; i < state.snapshotCount; i++) {
            if (!state.snapshots[i].isOffscreen) { mainSnap = state.snapshots[i]; mainSnapIndex = i; break; }
        }

        if (mainSnap != null && mainSnap.entityCount > 0) {
            try (MemoryStack stack = stackPush()) {
                VkClearValue.Buffer depthClear = VkClearValue.calloc(1, stack);
                depthClear.get(0).depthStencil().depth(1.0f).stencil(0);

                VkRenderPassBeginInfo rpInfo = VkRenderPassBeginInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO).renderPass(hizRenderPass).framebuffer(hizFramebuffer).pClearValues(depthClear);
                rpInfo.renderArea().offset().set(0, 0); rpInfo.renderArea().extent(swapchainExtent);

                vkCmdBeginRenderPass(cmd, rpInfo, VK_SUBPASS_CONTENTS_INLINE);
                vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, VKShader.hizPipeline);
                vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, VKShader.hizPipelineLayout, 0, stack.longs(VKShader.bindlessDescriptorSet), null);

                VkViewport.Buffer viewport = VkViewport.calloc(1, stack).x(0.0f).y(0.0f).width(swapchainExtent.width()).height(swapchainExtent.height()).minDepth(0.0f).maxDepth(1.0f);
                vkCmdSetViewport(cmd, 0, viewport);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack).offset(VkOffset2D.calloc(stack).set(0, 0)).extent(swapchainExtent);
                vkCmdSetScissor(cmd, 0, scissor);

                vkCmdBindIndexBuffer(cmd, GeomRegistry.gpuInd, 0, VK_INDEX_TYPE_UINT32);
                VKShader.pushDepthGlobalData(cmd, mainSnap.vpMatrix.address());

                // ONLY DRAW THE FIRST 500,000 SLOTS (The Walls!)
                long indirectOffset = frameIndirectOffset + (mainSnapIndex * MAX_MESHLETS_PER_SCENE * STRIDE_INDIRECT);
                vkCmdDrawIndexedIndirect(cmd, globalIndirectBuffer, indirectOffset, 500000, (int)STRIDE_INDIRECT);
                vkCmdEndRenderPass(cmd);

                VkImageMemoryBarrier.Buffer toGeneral = VkImageMemoryBarrier.calloc(1, stack).sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER).oldLayout(VK_IMAGE_LAYOUT_UNDEFINED).newLayout(VK_IMAGE_LAYOUT_GENERAL).srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).image(hizImage).srcAccessMask(0).dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT);
                toGeneral.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(hizMipLevels).baseArrayLayer(0).layerCount(1);
                vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, null, null, toGeneral);

                vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, VKShader.hizDownsamplePipeline);
                vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, VKShader.hizDownsamplePipelineLayout, 0, stack.longs(VKShader.bindlessDescriptorSet), null);

                int mipWidth = swapchainExtent.width(); int mipHeight = swapchainExtent.height();
                VKShader.pushHiZDownsampleData(cmd, 3999, 0, 1.0f / mipWidth, 1.0f / mipHeight, 1);
                vkCmdDispatch(cmd, (mipWidth + 15) / 16, (mipHeight + 15) / 16, 1);

                VkMemoryBarrier.Buffer mipBarrier = VkMemoryBarrier.calloc(1, stack).sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER).srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, mipBarrier, null, null);

                for (int mip = 1; mip < hizMipLevels; mip++) {
                    mipWidth = Math.max(1, mipWidth / 2); mipHeight = Math.max(1, mipHeight / 2);
                    VKShader.pushHiZDownsampleData(cmd, 4000 + (mip - 1), mip, 1.0f / mipWidth, 1.0f / mipHeight, 0);
                    vkCmdDispatch(cmd, (mipWidth + 15) / 16, (mipHeight + 15) / 16, 1);
                    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, mipBarrier, null, null);
                }
            }
        }

        // ========================================================================
        // PHASE 4.2.7: COMPUTE PASS 2 (CULL THE FOXES USING NEW HI-Z)
        // ========================================================================
        for (int i = 0; i < state.snapshotCount; i++) {
            SceneSnapshot snap = state.snapshots[i];
            if (snap.entityCount > 0) {
                VKShader.bindComputeShader(cmd);
                int trueInstanceOffset = (currentFrame * MAX_ENTITIES_PER_FRAME) + snap.globalEntityOffset;
                int commandOffset = (currentFrame * MAX_COMMANDS_PER_FRAME) + (i * MAX_MESHLETS_PER_SCENE);
                int globalSnapshotIndex = (currentFrame * 10) + i;

                // Notice the 1.0f at the end for passType!
                VKShader.pushComputeData(cmd, snap.vpMatrix.address(), snap.camX, snap.camY, snap.camZ, snap.p11, (float)swapchainExtent.width(), (float)swapchainExtent.height(), (float)hizMipLevels, trueInstanceOffset, commandOffset, globalSnapshotIndex, 1.0f);
                vkCmdDispatch(cmd, 16, snap.entityCount, 1);
            }
        }

        try (MemoryStack stack = stackPush()) {
            VkMemoryBarrier.Buffer computeBarrier = VkMemoryBarrier.calloc(1, stack).sType(VK_STRUCTURE_TYPE_MEMORY_BARRIER).srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT).dstAccessMask(VK_ACCESS_INDIRECT_COMMAND_READ_BIT);
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_DRAW_INDIRECT_BIT, 0, computeBarrier, null, null);
        }


        // ========================================================================
        // PHASE 4.3: OFFSCREEN FBO RENDER (UI & Portals)
        // ========================================================================
        for (int i = 0; i < state.snapshotCount; i++) {
            SceneSnapshot snap = state.snapshots[i];
            if (snap.isOffscreen) {
                RenderTarget rt = renderTargets[snap.containerId];

                if (rt == null || rt.width != snap.width || rt.height != snap.height) {
                    if (rt != null) rt.destroy();
                    if (snap.width > 0 && snap.height > 0) {
                        rt = new RenderTarget(snap.width, snap.height);
                        rt.init();
                        renderTargets[snap.containerId] = rt;
                    }
                }

                if (rt != null) {
                    rt.bind(cmd, snap.bgR, snap.bgG, snap.bgB, snap.bgA);

                    if (snap.entityCount > 0) {
                        VKShader.bindUbershader(cmd);
                        vkCmdBindIndexBuffer(cmd, GeomRegistry.gpuInd, 0, VK_INDEX_TYPE_UINT32);
                        VKShader.pushGlobalData(cmd, snap.vpMatrix.address());

                        long indirectOffset = frameIndirectOffset + (i * MAX_MESHLETS_PER_SCENE * STRIDE_INDIRECT);
                        vkCmdDrawIndexedIndirect(cmd, globalIndirectBuffer, indirectOffset, MAX_MESHLETS_PER_SCENE, (int)STRIDE_INDIRECT);
                    }

                    rt.unbind(cmd);
                }
            }
        }

        // ========================================================================
        // PHASE 4.4: MAIN SCREEN DRAW
        // ========================================================================
        Alloc.swapchainPass(imageIndex);
        vkCmdBeginRenderPass(cmd, Alloc.renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        VKShader.bindUbershader(cmd);

        try (MemoryStack stack = stackPush()) {
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0.0f).y(0.0f)
                    .width(swapchainExtent.width())
                    .height(swapchainExtent.height())
                    .minDepth(0.0f).maxDepth(1.0f);

            vkCmdSetViewport(cmd, 0, viewport);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.offset().set(0, 0);
            scissor.extent(swapchainExtent);
            vkCmdSetScissor(cmd, 0, scissor);

            vkCmdBindIndexBuffer(cmd, GeomRegistry.gpuInd, 0, VK_INDEX_TYPE_UINT32);

            for (int i = 0; i < state.snapshotCount; i++) {
                if (!state.snapshots[i].isOffscreen) {
                    mainSnap = state.snapshots[i];

                    if (mainSnap.entityCount > 0) {
                        VKShader.pushGlobalData(cmd, mainSnap.vpMatrix.address());

                        long indirectOffset = frameIndirectOffset + (i * MAX_MESHLETS_PER_SCENE * STRIDE_INDIRECT);

                        // Draw the Occluders
                        vkCmdDrawIndexedIndirect(cmd, globalIndirectBuffer, indirectOffset, 500000, (int)STRIDE_INDIRECT);
                        // Draw the Occlude-es
                        vkCmdDrawIndexedIndirect(cmd, globalIndirectBuffer, indirectOffset + (500000 * STRIDE_INDIRECT), 500000, (int)STRIDE_INDIRECT);
                    }
                }
            }

            // --- UI PASS ---
            if (state.uiElementCount > 0) {
                mesh.Mesh uiSquare = loader.MeshRegistry.get("square");

                for (int i = 0; i < state.uiElementCount; i++) {
                    int containerId = state.uiTextureIds.get(i);
                    RenderTarget rt = renderTargets[containerId];

                    if (rt != null) {
                        long matrixAddress = state.uiTransforms.address() + (i * 16L * 4L);
                        VKShader.pushUIState(cmd, matrixAddress, rt.textureId, uiSquare.globalVertexOffset, (float) Window.getWidth(), (float)Window.getHeight());
                        vkCmdDrawIndexed(cmd, uiSquare.indexCount, 1, uiSquare.globalIndexOffset, uiSquare.globalVertexOffset, 0);
                    }
                }
            }
        }

        vkCmdEndRenderPass(cmd);
        vkEndCommandBuffer(cmd);

        Alloc.submitAndPresent(imageIndex);
        int submitResult = vkQueueSubmit(VulkanContext.getGraphicsQueue(), Alloc.submitInfo, inFlightFences[currentFrame]);
        if (submitResult != VK_SUCCESS) throw new RuntimeException("GPU CRASH! vkQueueSubmit returned: " + submitResult);

        int presentResult = Alloc.queuePresentInfo();
        if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR || hardware.Window.wasResized) {
            hardware.Window.wasResized = false;
            recreateSwapchain();
        }

        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
    }

    private static void cleanupSwapchain() {
        VkDevice device = VulkanContext.getDevice();

        // ... Inside cleanupSwapchain() ...

        if (hizImageMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, hizImageMemory, null);
            hizImageMemory = VK_NULL_HANDLE;
        }

        // ---> NEW: Destroy the Prepass Depth Buffer! <---
        if (prepassDepthView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, prepassDepthView, null);
            prepassDepthView = VK_NULL_HANDLE;
        }
        if (prepassDepthImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, prepassDepthImage, null);
            prepassDepthImage = VK_NULL_HANDLE;
        }
        if (prepassDepthMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, prepassDepthMemory, null);
            prepassDepthMemory = VK_NULL_HANDLE;
        }

        // ---> NEW: Destroy the Hi-Z FBO and Render Pass! <---
        if (hizFramebuffer != VK_NULL_HANDLE) {
            vkDestroyFramebuffer(device, hizFramebuffer, null);
            hizFramebuffer = VK_NULL_HANDLE;
        }
        if (hizRenderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, hizRenderPass, null);
            hizRenderPass = VK_NULL_HANDLE;
        }

        // ---> NEW: Destroy the Hi-Z Pipelines! <---
        if (VKShader.hizPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, VKShader.hizPipeline, null);
            vkDestroyPipelineLayout(device, VKShader.hizPipelineLayout, null);
            VKShader.hizPipeline = VK_NULL_HANDLE;
            VKShader.hizPipelineLayout = VK_NULL_HANDLE;
        }
        if (VKShader.hizDownsamplePipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, VKShader.hizDownsamplePipeline, null);
            vkDestroyPipelineLayout(device, VKShader.hizDownsamplePipelineLayout, null);
            VKShader.hizDownsamplePipeline = VK_NULL_HANDLE;
            VKShader.hizDownsamplePipelineLayout = VK_NULL_HANDLE;
        }

        // Destroy the individual slices
        if (hizMipViews != null) {
            for (long view : hizMipViews) {
                if (view != VK_NULL_HANDLE) vkDestroyImageView(device, view, null);
            }
            hizMipViews = null;
        }
        // ---> NEW: Destroy the Hi-Z Pyramid memory! <---
        if (hizSampler != VK_NULL_HANDLE) {
            vkDestroySampler(device, hizSampler, null);
            hizSampler = VK_NULL_HANDLE;
        }
        if (hizImageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, hizImageView, null);
            hizImageView = VK_NULL_HANDLE;
        }
        if (hizImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, hizImage, null);
            hizImage = VK_NULL_HANDLE;
        }
        if (hizImageMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, hizImageMemory, null);
            hizImageMemory = VK_NULL_HANDLE;
        }

        for (long framebuffer : framebuffers) {
            vkDestroyFramebuffer(device, framebuffer, null);
        }

        if (VKShader.ubershaderPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, VKShader.ubershaderPipeline, null);
            vkDestroyPipelineLayout(device, VKShader.pipelineLayout, null);
            vkDestroyPipeline(device, VKShader.cullingPipeline, null);
            vkDestroyPipelineLayout(device, VKShader.computePipelineLayout, null);
            VKShader.ubershaderPipeline = VK_NULL_HANDLE;
            VKShader.pipelineLayout = VK_NULL_HANDLE;
        }

        if (renderPass != VK_NULL_HANDLE)
            vkDestroyRenderPass(device, renderPass, null);

        if (depthImageView != VK_NULL_HANDLE)
            vkDestroyImageView(device, depthImageView, null);

        if (depthImage != VK_NULL_HANDLE)
            vkDestroyImage(device, depthImage, null);

        if (depthImageMemory != VK_NULL_HANDLE)
            vkFreeMemory(device, depthImageMemory, null);

        for (long imageView : swapchainImageViews) {
            vkDestroyImageView(device, imageView, null);
        }

        if (renderFinishedSemaphores != null) {
            for (long sem : renderFinishedSemaphores) {
                if (sem != VK_NULL_HANDLE) vkDestroySemaphore(device, sem, null);
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

        // 1. Rebuild the core surfaces
        createSwapchain();
        createDepthResources();

        // ---> THE FIX: Rebuild the Hi-Z memory and slice it! <---
        createHiZResources();

        createImageViews();

        // 2. Rebuild BOTH Render Passes
        createRenderPass();
        createHiZRenderPass();

        // 3. Rebuild the Shaders (Because the viewport size changed)
        VKShader.initUberShader(VulkanContext.getDevice(), renderPass, swapchainExtent, "vertex", "fragment");
        VKShader.initDepthPipeline(device, hizRenderPass, swapchainExtent, "vertex"); // MUST REBUILD PRE-PASS PIPELINE
        VKShader.initComputeShader(VulkanContext.getDevice());
        VKShader.initHiZCompute(device);

        imagesInFlight = new long[swapchainImages.length];
        renderFinishedSemaphores = new long[swapchainImages.length];

        try (MemoryStack stack = stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            LongBuffer pSemaphore = stack.mallocLong(1);

            for (int i = 0; i < swapchainImages.length; i++) {
                vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
                renderFinishedSemaphores[i] = pSemaphore.get(0);
            }
        }

        // 4. Rebuild BOTH sets of Framebuffers
        createFramebuffers();
        createHiZFramebuffer();

        Alloc.checkRenderPassInfo();
    }

    public static void destroy() {
        VkDevice device = VulkanContext.getDevice();
        if (device != null) {
            vkDeviceWaitIdle(device);
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
            if (globalEntityBuffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, globalEntityBuffer, null);
                vkFreeMemory(device, globalEntityMemory, null);
            }
            if (globalIndirectBuffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, globalIndirectBuffer, null);
                vkFreeMemory(device, globalIndirectMemory, null);
            }
            if (atomicCounterBuffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(device, atomicCounterBuffer, null);
                vkFreeMemory(device, atomicCounterMemory, null);
            }
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

    public static int getSwapchainImageFormat() { return swapchainImageFormat; }
    public static long getCommandPool() { return commandPool; }
    public static long getMappedEntityPointer() { return mappedEntityPointer; }

    public static class Alloc {
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

        public static void freeAll() {
            if(beginInfo != null) {
                beginInfo.free(); clearValues.free(); renderArea.free();
                renderPassInfo.free(); submitInfo.free(); presentInfo.free();
                MemoryUtil.memFree(imageIndex); MemoryUtil.memFree(pWaitSemaphores);
                MemoryUtil.memFree(pWaitDstStageMask); MemoryUtil.memFree(pCommandBuffers);
                MemoryUtil.memFree(pSignalSemaphores); MemoryUtil.memFree(pSwapchains);
            }
        }

        public static void swapchainPass(int index) {
            renderArea.offset().set(0, 0);
            renderArea.extent(swapchainExtent);
            renderPassInfo.renderArea(renderArea);
            renderPassInfo.pClearValues(clearValues);
            renderPassInfo.framebuffer(framebuffers[index]);
        }

        public static void submitAndPresent(int index) {
            submitInfo.pWaitSemaphores(pWaitSemaphores.put(0, imageAvailableSemaphores[currentFrame]));
            submitInfo.pCommandBuffers(pCommandBuffers.put(0, commandBuffers[currentFrame]));
            submitInfo.pSignalSemaphores(pSignalSemaphores.put(0, renderFinishedSemaphores[index]));
        }

        public static int queuePresentInfo() {
            presentInfo.pWaitSemaphores(pSignalSemaphores);
            presentInfo.pImageIndices(imageIndex);
            return vkQueuePresentKHR(VulkanContext.getPresentQueue(), presentInfo);
        }

        public static void checkRenderPassInfo() {
            if (renderPassInfo != null) {
                renderPassInfo.renderPass(renderPass);
                pSwapchains.put(0, swapchain);
            }
        }
    }

    public static long getGlobalEntityBuffer() { return globalEntityBuffer; }
}