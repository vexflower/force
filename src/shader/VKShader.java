package shader;

import entity.Light;
import lang.Mat4;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import util.FastList;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.vulkan.VK10.*;
// [CHANGE 1]: We need Vulkan 1.2 imports for Bindless features!
import static org.lwjgl.vulkan.VK12.*;

/**
 * AAA Vulkan Shader Manager.
 * Fully Fat-Jar compatible. Loads pre-compiled SPIR-V binaries directly into off-heap memory.
 */
public final class VKShader {

    private static ShaderPipeline currentPipeline;

    // 16 floats = 64 bytes. Kept off-heap for zero-allocation Matrix pushing.
    private static final FloatBuffer PUSH_CONSTANT_BUFFER = memAllocFloat(16);

    // =========================================================================================
    // [CHANGE 2]: GLOBAL BINDLESS TEXTURE ARCHITECTURE (The "Phonebook")
    // =========================================================================================
    public static final int MAX_BINDLESS_TEXTURES = 4096;
    public static long bindlessDescriptorSetLayout;
    public static long bindlessDescriptorPool;
    public static long bindlessDescriptorSet;

    /**
     * MUST be called immediately after the Logical Device is created in Display.java,
     * before compiling any pipelines.
     */
    public static void initBindlessHardware(VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            System.out.println("Initializing Global Bindless Texture Array (" + MAX_BINDLESS_TEXTURES + " slots)...");

            // 1. Describe the Layout (Binding 0 = Array of 4096 Textures)
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.binding(0);
            bindings.descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            bindings.descriptorCount(MAX_BINDLESS_TEXTURES);
            bindings.stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            // 2. Enable Bindless Features for this Layout (Allows us to update slots while running)
            VkDescriptorSetLayoutBindingFlagsCreateInfo layoutFlags = VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack);
            layoutFlags.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO);
            layoutFlags.pBindingFlags(stack.ints(
                    VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT |
                            VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT
            ));

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack);
            layoutInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);
            layoutInfo.pBindings(bindings);
            layoutInfo.flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT);
            layoutInfo.pNext(layoutFlags.address());

            LongBuffer pSetLayout = stack.mallocLong(1);
            if (vkCreateDescriptorSetLayout(device, layoutInfo, null, pSetLayout) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Bindless Descriptor Set Layout!");
            }
            bindlessDescriptorSetLayout = pSetLayout.get(0);

            // 3. Allocate the Global Pool
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            poolSizes.descriptorCount(MAX_BINDLESS_TEXTURES);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);
            poolInfo.pPoolSizes(poolSizes);
            poolInfo.maxSets(1); // We only need ONE global set!
            poolInfo.flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT);

            LongBuffer pPool = stack.mallocLong(1);
            if (vkCreateDescriptorPool(device, poolInfo, null, pPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Bindless Descriptor Pool!");
            }
            bindlessDescriptorPool = pPool.get(0);

            // 4. Allocate the single Global Descriptor Set (This is the actual "Phonebook" handle)
            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack);
            allocInfo.sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);
            allocInfo.descriptorPool(bindlessDescriptorPool);
            allocInfo.pSetLayouts(stack.longs(bindlessDescriptorSetLayout));

            LongBuffer pDescriptorSet = stack.mallocLong(1);
            if (vkAllocateDescriptorSets(device, allocInfo, pDescriptorSet) != VK_SUCCESS) {
                throw new RuntimeException("Failed to allocate Bindless Descriptor Set!");
            }
            bindlessDescriptorSet = pDescriptorSet.get(0);
        }
    }
    // =========================================================================================

    public static abstract class ShaderPipeline {
        public long pipelineLayout;
        public long graphicsPipeline;

        protected int matrixOffset;
        protected int materialOffset;

        public ShaderPipeline(VkDevice device, long renderPass, VkExtent2D extent, String vertName, String fragName) {
            long vertModule = loadShaderModule(device, "/shader/vertex/" + vertName + ".spv");
            long fragModule = loadShaderModule(device, "/shader/fragment/" + fragName + ".spv");

            try (MemoryStack stack = stackPush()) {
                // 1. Programmable Shader Stages
                VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
                shaderStages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO).stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertModule).pName(stack.UTF8("main"));
                shaderStages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO).stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragModule).pName(stack.UTF8("main"));

                // [CHANGED: Tell Vulkan exactly how to read our Positions (Binding 0) and UVs (Binding 1)]
                VkVertexInputBindingDescription.Buffer bindingDescription = VkVertexInputBindingDescription.calloc(2, stack);
                bindingDescription.get(0).binding(0).stride(3 * 4).inputRate(VK_VERTEX_INPUT_RATE_VERTEX); // 3 floats for XYZ
                bindingDescription.get(1).binding(1).stride(2 * 4).inputRate(VK_VERTEX_INPUT_RATE_VERTEX); // 2 floats for UV

                VkVertexInputAttributeDescription.Buffer attributeDescription = VkVertexInputAttributeDescription.calloc(2, stack);
                // Layout 0: Positions
                attributeDescription.get(0).binding(0).location(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                // Layout 1: UVs
                attributeDescription.get(1).binding(1).location(1).format(VK_FORMAT_R32G32_SFLOAT).offset(0);

                VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack);
                vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
                vertexInputInfo.pVertexBindingDescriptions(bindingDescription);
                vertexInputInfo.pVertexAttributeDescriptions(attributeDescription);

                // 3. Input Assembly (Triangles)
                VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO).topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST).primitiveRestartEnable(false);


                // 4. Viewport & Scissor
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack).x(0.0f).y(0.0f).width(extent.width()).height(extent.height()).minDepth(0.0f).maxDepth(1.0f);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack).offset(VkOffset2D.calloc(stack).set(0, 0)).extent(extent);
                VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO).pViewports(viewport).pScissors(scissor);

                // 5. Rasterizer
                VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                        .depthClampEnable(false)
                        .rasterizerDiscardEnable(false)
                        .polygonMode(VK_POLYGON_MODE_FILL)
                        .lineWidth(1.0f)
                        // [THE FIX]: Change VK_CULL_MODE_NONE to VK_CULL_MODE_BACK_BIT
                        .cullMode(VK_CULL_MODE_BACK_BIT)
                        .frontFace(VK_FRONT_FACE_CLOCKWISE);

                // [NEW] 5.1. Depth Testing!
                VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                        .depthTestEnable(true)
                        .depthWriteEnable(true)
                        // [THE FIX]: Change LESS to LESS_OR_EQUAL so UI panels at Z=0.0 can stack!
                        .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                        .depthBoundsTestEnable(false)
                        .stencilTestEnable(false);

                // 6. Multisampling
                VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO).sampleShadingEnable(false).rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

                // 7. Color Blending
                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                        .blendEnable(true)
                        .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                        .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                        .colorBlendOp(VK_BLEND_OP_ADD)
                        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                        .alphaBlendOp(VK_BLEND_OP_ADD);
                VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO).logicOpEnable(false).pAttachments(colorBlendAttachment);

                // 8. Pipeline Layout (Push Constants setup)
                VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack);
                pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
                pushConstants.offset(0);

                // 64 (Mat4) + 4 (texId) + 4 (renderType) + 8 (vec2 screenSize) = 80 bytes
                pushConstants.size(80);

                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack);
                pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
                pipelineLayoutInfo.pPushConstantRanges(pushConstants);

                // =========================================================================================
                // [CHANGE 3]: Tell the Pipeline about the Phonebook!
                // Without this line, the shader will crash when it tries to read the texture array.
                // =========================================================================================
                pipelineLayoutInfo.pSetLayouts(stack.longs(bindlessDescriptorSetLayout));

                LongBuffer pPipelineLayout = stack.mallocLong(1);
                if (vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create pipeline layout!");
                }
                pipelineLayout = pPipelineLayout.get(0);

                // 9. Create the Final Graphics Pipeline
                VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
                pipelineInfo.sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);
                pipelineInfo.pStages(shaderStages);
                pipelineInfo.pVertexInputState(vertexInputInfo);
                pipelineInfo.pInputAssemblyState(inputAssembly);
                pipelineInfo.pViewportState(viewportState);
                pipelineInfo.pRasterizationState(rasterizer);
                pipelineInfo.pDepthStencilState(depthStencil); // [CRITICAL]: Add this line!
                pipelineInfo.pMultisampleState(multisampling);
                pipelineInfo.pColorBlendState(colorBlending);
                pipelineInfo.layout(pipelineLayout);
                pipelineInfo.renderPass(renderPass);
                pipelineInfo.subpass(0);

                LongBuffer pGraphicsPipeline = stack.mallocLong(1);
                if (vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline) != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create graphics pipeline!");
                }
                graphicsPipeline = pGraphicsPipeline.get(0);
            }

            vkDestroyShaderModule(device, vertModule, null);
            vkDestroyShaderModule(device, fragModule, null);

            defineByteOffsets();
        }

        protected abstract void defineByteOffsets();
    }

    public static void bind(ShaderPipeline pipeline, VkCommandBuffer commandBuffer) {
        currentPipeline = pipeline;
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.graphicsPipeline);
    }

    public static void unbind() {
        currentPipeline = null;
    }

    public static void loadMatrix(VkCommandBuffer commandBuffer, int byteOffset, Mat4 matrix) {
        PUSH_CONSTANT_BUFFER.clear();
        matrix.store(PUSH_CONSTANT_BUFFER);
        PUSH_CONSTANT_BUFFER.flip();

        vkCmdPushConstants(commandBuffer, currentPipeline.pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT, byteOffset, PUSH_CONSTANT_BUFFER);
    }

    private static long loadShaderModule(VkDevice device, String resourcePath) {
        ByteBuffer shaderCode = readResourceToOffHeap(resourcePath);
        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(shaderCode);
            LongBuffer pShaderModule = stack.mallocLong(1);
            if (vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan shader module from: " + resourcePath);
            }
            MemoryUtil.memFree(shaderCode);
            return pShaderModule.get(0);
        }
    }

    private static ByteBuffer readResourceToOffHeap(String resourcePath) {
        try (InputStream stream = VKShader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) throw new RuntimeException("SPIR-V Shader not found in classpath: " + resourcePath);
            int bufferSize = 4096;
            ByteBuffer buffer = MemoryUtil.memAlloc(bufferSize);
            byte[] chunk = new byte[1024];
            int bytesRead;
            int totalBytes = 0;
            while ((bytesRead = stream.read(chunk)) != -1) {
                if (totalBytes + bytesRead > bufferSize) {
                    bufferSize *= 2;
                    buffer = MemoryUtil.memRealloc(buffer, bufferSize);
                }
                buffer.put(chunk, 0, bytesRead);
                totalBytes += bytesRead;
            }
            buffer.flip();
            return buffer;
        } catch (Exception e) {
            throw new RuntimeException("Failed to stream shader resource: " + resourcePath, e);
        }
    }

    /**
     * Dynamically maps a Vulkan Image to a specific slot in the Global Bindless Array.
     */
    public static void updateBindlessTexture(int textureId, long imageView, long sampler) {
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            // We tell the GPU to expect this image to be in a READ_ONLY state when sampled
            imageInfo.imageLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            imageInfo.imageView(imageView);
            imageInfo.sampler(sampler);

            VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1, stack);
            descriptorWrite.sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
            descriptorWrite.dstSet(bindlessDescriptorSet); // The Global Set
            descriptorWrite.dstBinding(0);
            descriptorWrite.dstArrayElement(textureId);    // The exact array index!
            descriptorWrite.descriptorType(org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            descriptorWrite.descriptorCount(1);
            descriptorWrite.pImageInfo(imageInfo);

            org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets(hardware.Display.getDevice(), descriptorWrite, null);
        }
    }

    public static class EntityShaderPipeline extends ShaderPipeline {
        public static EntityShaderPipeline pipeline = null;

        public EntityShaderPipeline(VkDevice device, long renderPass, VkExtent2D extent) {
            super(device, renderPass, extent, "vertex", "fragment");
        }

        // [NEW: Fast-path for pushing matrices directly from the flat RenderState array]
        public static void loadTransformationMatrixArray(VkCommandBuffer commandBuffer, float[] matrixData, int offset) {
            // 1. Reset our zero-allocation off-heap buffer
            PUSH_CONSTANT_BUFFER.clear();

            // 2. Dump exactly 16 floats (one Mat4) into the C-buffer
            PUSH_CONSTANT_BUFFER.put(matrixData, offset, 16);
            PUSH_CONSTANT_BUFFER.flip();

            // 3. Blast it to the GPU Push Constants memory!
            vkCmdPushConstants(
                    commandBuffer,
                    pipeline.pipelineLayout,
                    VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                    pipeline.matrixOffset,
                    PUSH_CONSTANT_BUFFER
            );
        }

        @Override
        protected void defineByteOffsets() {
            this.matrixOffset = 0;
            this.materialOffset = 64;
        }

        public static void loadTransformationMatrix(VkCommandBuffer commandBuffer, Mat4 matrix) {
            loadMatrix(commandBuffer, pipeline.matrixOffset, matrix);
        }

        public static void bindLights(VkCommandBuffer commandBuffer, FastList<Light> lights) {
            // Descriptor sets will go here
        }
    }
}