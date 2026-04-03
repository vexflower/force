package shader;

import hardware.VulkanContext;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

/**
 * AAA Vulkan Ubershader Manager.
 * Acts as the absolute heart of the renderer. Handles the global pipeline,
 * bindless textures, and zero-allocation push constant streaming.
 */
public final class VKShader {

    public static long pipelineLayout;
    public static long ubershaderPipeline;

    // Buffer sized for 80 bytes (20 floats total)
    // 0-15: Matrix (64 bytes)
    // 16: Texture ID (4 bytes - cast to float)
    // 17: Render Type (4 bytes - cast to float)
    // 18: Screen X / Padding (4 bytes)
    // 19: Screen Y / Padding (4 bytes)
    private static final FloatBuffer PUSH_CONSTANT_BUFFER = memAllocFloat(20);

    // --- BINDLESS HARDWARE ---
    public static final int MAX_BINDLESS_TEXTURES = 4096;
    public static long bindlessDescriptorSetLayout;
    public static long bindlessDescriptorPool;
    public static long bindlessDescriptorSet;

    public static void initBindlessHardware(VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            System.out.println("Initializing Global Bindless Texture Array...");

            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.binding(0).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(MAX_BINDLESS_TEXTURES).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutBindingFlagsCreateInfo layoutFlags = VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO)
                    .pBindingFlags(stack.ints(VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT));

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindings)
                    .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT)
                    .pNext(layoutFlags.address());

            LongBuffer pSetLayout = stack.mallocLong(1);
            vkCreateDescriptorSetLayout(device, layoutInfo, null, pSetLayout);
            bindlessDescriptorSetLayout = pSetLayout.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(MAX_BINDLESS_TEXTURES);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO).pPoolSizes(poolSizes)
                    .maxSets(1).flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT);

            LongBuffer pPool = stack.mallocLong(1);
            vkCreateDescriptorPool(device, poolInfo, null, pPool);
            bindlessDescriptorPool = pPool.get(0);

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(bindlessDescriptorPool)
                    .pSetLayouts(stack.longs(bindlessDescriptorSetLayout));

            LongBuffer pDescriptorSet = stack.mallocLong(1);
            vkAllocateDescriptorSets(device, allocInfo, pDescriptorSet);
            bindlessDescriptorSet = pDescriptorSet.get(0);
        }
    }

    public static void updateBindlessTexture(int textureId, long imageView, long sampler) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL).imageView(imageView).sampler(sampler);

            VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(0).dstArrayElement(textureId)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
                    .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(VulkanContext.getDevice(), descriptorWrite, null);
        }
    }

    // --- UBERSHADER COMPILATION ---
    public static void initUberShader(VkDevice device, long renderPass, VkExtent2D extent, String vertName, String fragName) {
        long vertModule = loadShaderModule(device, "/shader/vertex/" + vertName + ".spv");
        long fragModule = loadShaderModule(device, "/shader/fragment/" + fragName + ".spv");

        try (MemoryStack stack = stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO).stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertModule).pName(stack.UTF8("main"));
            shaderStages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO).stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragModule).pName(stack.UTF8("main"));

            VkVertexInputBindingDescription.Buffer bindingDesc = VkVertexInputBindingDescription.calloc(2, stack);
            bindingDesc.get(0).binding(0).stride(3 * 4).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
            bindingDesc.get(1).binding(1).stride(2 * 4).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkVertexInputAttributeDescription.Buffer attrDesc = VkVertexInputAttributeDescription.calloc(2, stack);
            attrDesc.get(0).binding(0).location(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
            attrDesc.get(1).binding(1).location(1).format(VK_FORMAT_R32G32_SFLOAT).offset(0);

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(bindingDesc).pVertexAttributeDescriptions(attrDesc);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO).topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack).x(0.0f).y(0.0f).width(extent.width()).height(extent.height()).minDepth(0.0f).maxDepth(1.0f);
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack).offset(VkOffset2D.calloc(stack).set(0, 0)).extent(extent);
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO).pViewports(viewport).pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .polygonMode(VK_POLYGON_MODE_FILL).lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_BACK_BIT).frontFace(VK_FRONT_FACE_CLOCKWISE);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true).depthWriteEnable(true).depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO).rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(true).srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA).dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD).srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE).dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO).alphaBlendOp(VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO).pAttachments(colorBlendAttachment);

            // Total Size: 80 bytes for the Uber shader
            VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT).offset(0).size(80);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pPushConstantRanges(pushConstants)
                    .pSetLayouts(stack.longs(bindlessDescriptorSetLayout));

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
            pipelineLayout = pPipelineLayout.get(0);

            // Tell Vulkan we will change the Viewport and Scissor on the fly
            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages).pVertexInputState(vertexInputInfo).pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState).pRasterizationState(rasterizer).pDepthStencilState(depthStencil)
                    .pMultisampleState(multisampling).pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayout).renderPass(renderPass).subpass(0);

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);
            vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline);
            ubershaderPipeline = pGraphicsPipeline.get(0);
        }

        vkDestroyShaderModule(device, vertModule, null);
        vkDestroyShaderModule(device, fragModule, null);
    }

    // --- RENDER LOOP HELPERS ---

    public static void bindUbershader(VkCommandBuffer cmd) {
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, ubershaderPipeline);

        try (MemoryStack stack = stackPush()) {
            vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipelineLayout, 0, stack.longs(bindlessDescriptorSet), null);
        }
    }

    /**
     * Fast-path for pushing 3D Entity state (Render Type 0)
     */
    public static void pushEntityState(VkCommandBuffer cmd, float[] matrixData, int matrixOffset, int texId) {
        PUSH_CONSTANT_BUFFER.clear();
        PUSH_CONSTANT_BUFFER.put(matrixData, matrixOffset, 16);
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(texId));
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(0)); // Type 0 = 3D Entity

        // ---> THE FIX: Pad the remaining 8 bytes so the GPU doesn't crash <---
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);

        PUSH_CONSTANT_BUFFER.flip();
        vkCmdPushConstants(cmd, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, PUSH_CONSTANT_BUFFER);
    }

    /**
     * Fast-path for pushing 2D UI state (Render Type 1)
     */
    public static void pushUIState(VkCommandBuffer cmd, float[] matrixData, int matrixOffset, int texId, float screenW, float screenH) {
        PUSH_CONSTANT_BUFFER.clear();
        PUSH_CONSTANT_BUFFER.put(matrixData, matrixOffset, 16);

        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(texId));
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(1)); // Type 1 = UI
        PUSH_CONSTANT_BUFFER.put(screenW);
        PUSH_CONSTANT_BUFFER.put(screenH);

        PUSH_CONSTANT_BUFFER.flip();
        vkCmdPushConstants(cmd, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, PUSH_CONSTANT_BUFFER);
    }

    private static long loadShaderModule(VkDevice device, String resourcePath) {
        ByteBuffer shaderCode = readResourceToOffHeap(resourcePath);
        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO).pCode(shaderCode);
            LongBuffer pModule = stack.mallocLong(1);
            vkCreateShaderModule(device, createInfo, null, pModule);
            MemoryUtil.memFree(shaderCode);
            return pModule.get(0);
        }
    }

    private static ByteBuffer readResourceToOffHeap(String resourcePath) {
        try (InputStream stream = VKShader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) throw new RuntimeException("SPIR-V Shader not found: " + resourcePath);
            int bufferSize = 4096, bytesRead, totalBytes = 0;
            ByteBuffer buffer = MemoryUtil.memAlloc(bufferSize);
            byte[] chunk = new byte[1024];
            while ((bytesRead = stream.read(chunk)) != -1) {
                if (totalBytes + bytesRead > bufferSize) buffer = MemoryUtil.memRealloc(buffer, bufferSize *= 2);
                buffer.put(chunk, 0, bytesRead);
                totalBytes += bytesRead;
            }
            buffer.flip();
            return buffer;
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}