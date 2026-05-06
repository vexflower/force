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

public final class VKShader {

    public static long pipelineLayout;
    public static long ubershaderPipeline;
    public static long computePipelineLayout;
    public static long cullingPipeline;

    private static final FloatBuffer PUSH_CONSTANT_BUFFER = memAllocFloat(32);

    public static final int MAX_BINDLESS_TEXTURES = 4096;
    public static long bindlessDescriptorSetLayout;
    public static long bindlessDescriptorPool;
    public static long bindlessDescriptorSet;

    public static long hizPipelineLayout;
    public static long hizPipeline;
    public static long hizDownsamplePipelineLayout;
    public static long hizDownsamplePipeline;

    public static void initBindlessHardware(VkDevice device) {
        try (MemoryStack stack = stackPush()) {
            System.out.println("Initializing Global Bindless Vault (Hi-Z Enabled)...");

            // CHANGED: We need 12 slots to allow binding index 11!
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(12, stack);

            // Bindings 0-10 remain exactly as you have them
            bindings.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(MAX_BINDLESS_TEXTURES).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(1).binding(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(2).binding(2).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            bindings.get(3).binding(3).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            bindings.get(4).binding(4).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            bindings.get(5).binding(5).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
            bindings.get(6).binding(6).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(7).binding(7).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(8).binding(8).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(9).binding(9).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            bindings.get(10).binding(10).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);

            // NEW: Binding 11 for Hi-Z Mipmap writing (Storage Images)
            bindings.get(11).binding(11).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(16).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);

            VkDescriptorSetLayoutBindingFlagsCreateInfo layoutFlags = VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_BINDING_FLAGS_CREATE_INFO)
                    .pBindingFlags(stack.ints(
                            VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT,
                            VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT,
                            VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT,
                            VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT,
                            VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT,
                            VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT, VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT,
                            VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT // Slot 11
                    ));

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindings)
                    .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT)
                    .pNext(layoutFlags.address());

            LongBuffer pSetLayout = stack.mallocLong(1);
            vkCreateDescriptorSetLayout(device, layoutInfo, null, pSetLayout);
            bindlessDescriptorSetLayout = pSetLayout.get(0);

            // CHANGED: Must include a 3rd Pool Size for the Storage Images!
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(3, stack);
            poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(MAX_BINDLESS_TEXTURES);
            poolSizes.get(1).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(10);
            poolSizes.get(2).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(16);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(poolSizes)
                    .maxSets(1)
                    .flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT);

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

    public static void initDepthPipeline(VkDevice device, long renderPass, VkExtent2D extent, String vertName) {
        long vertModule = loadShaderModule(device, "/shader/spv/" + vertName + ".spv");

        try (MemoryStack stack = stackPush()) {
            // ONLY ONE STAGE: Vertex! No Fragment Shader!
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(1, stack);
            shaderStages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertModule)
                    .pName(stack.UTF8("main"));

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack).x(0.0f).y(0.0f).width(extent.width()).height(extent.height()).minDepth(0.0f).maxDepth(1.0f);
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack).offset(VkOffset2D.calloc(stack).set(0, 0)).extent(extent);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .pViewports(viewport).pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .polygonMode(VK_POLYGON_MODE_FILL).lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_BACK_BIT).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            // Empty Color Blend - We aren't writing any colors!
            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);

            VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0).size(108);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pPushConstantRanges(pushConstants)
                    .pSetLayouts(stack.longs(bindlessDescriptorSetLayout));

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
            hizPipelineLayout = pPipelineLayout.get(0);

            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInputInfo)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pDepthStencilState(depthStencil)
                    .pMultisampleState(multisampling)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(hizPipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0);

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);
            vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline);
            hizPipeline = pGraphicsPipeline.get(0);
        }
        vkDestroyShaderModule(device, vertModule, null);
    }

    public static void bindSSBOs(long entityBuffer, long posBuffer, long uvBuffer, long normBuffer, long tangBuffer, long boundsBuffer, long dataBuffer, long conesBuffer, long indirectBuffer, long atomicBuffer) {
        try (MemoryStack stack = stackPush()) {

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(10, stack);

            // 1. Entities
            writes.get(0).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(1).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(entityBuffer).offset(0).range(VK_WHOLE_SIZE));

            // 2. Positions
            writes.get(1).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(2).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(posBuffer).offset(0).range(VK_WHOLE_SIZE));

            // 3. UVs
            writes.get(2).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(3).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(uvBuffer).offset(0).range(VK_WHOLE_SIZE));

            // 4. Normals
            writes.get(3).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(4).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(normBuffer).offset(0).range(VK_WHOLE_SIZE));

            // 5. Tangents
            writes.get(4).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(5).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(tangBuffer).offset(0).range(VK_WHOLE_SIZE));

            // 6. Bounds
            writes.get(5).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(6).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(boundsBuffer).offset(0).range(VK_WHOLE_SIZE));

            // 7. Meshlet Data
            writes.get(6).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(7).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(dataBuffer).offset(0).range(VK_WHOLE_SIZE));

            // 8. Indirect Draw Commands
            writes.get(7).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(8).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(indirectBuffer).offset(0).range(VK_WHOLE_SIZE));

            // 9. Atomic Counters
            writes.get(8).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(9).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(atomicBuffer).offset(0).range(VK_WHOLE_SIZE));

            // 10. Cones
            writes.get(9).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(10).dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(conesBuffer).offset(0).range(VK_WHOLE_SIZE));

            vkUpdateDescriptorSets(VulkanContext.getDevice(), writes, null);
        }
    }

    public static void updateBindlessTexture(int textureId, long imageView, long sampler) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(imageView)
                    .sampler(sampler);

            VkWriteDescriptorSet.Buffer descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet)
                    .dstBinding(0)
                    .dstArrayElement(textureId)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(VulkanContext.getDevice(), descriptorWrite, null);
        }
    }

    public static void initUberShader(VkDevice device, long renderPass, VkExtent2D extent, String vertName, String fragName) {
        long vertModule = loadShaderModule(device, "/shader/spv/" + vertName + ".spv");
        long fragModule = loadShaderModule(device, "/shader/spv/" + fragName + ".spv");

        try (MemoryStack stack = stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);

            shaderStages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_VERTEX_BIT)
                    .module(vertModule)
                    .pName(stack.UTF8("main"));

            shaderStages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                    .module(fragModule)
                    .pName(stack.UTF8("main"));

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack)
                    .x(0.0f).y(0.0f)
                    .width(extent.width()).height(extent.height())
                    .minDepth(0.0f).maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack)
                    .offset(VkOffset2D.calloc(stack).set(0, 0))
                    .extent(extent);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .pViewports(viewport)
                    .pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_BACK_BIT)
                    //.cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                    .alphaBlendOp(VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .pAttachments(colorBlendAttachment);

            VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT)
                    .offset(0).size(96);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pPushConstantRanges(pushConstants)
                    .pSetLayouts(stack.longs(bindlessDescriptorSetLayout));

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
            pipelineLayout = pPipelineLayout.get(0);

            VkPipelineDynamicStateCreateInfo dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInputInfo)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pDepthStencilState(depthStencil)
                    .pMultisampleState(multisampling)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicState)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0);

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);
            vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline);
            ubershaderPipeline = pGraphicsPipeline.get(0);
        }

        vkDestroyShaderModule(device, vertModule, null);
        vkDestroyShaderModule(device, fragModule, null);
    }

    public static void initComputeShader(VkDevice device) {
        long compModule = loadShaderModule(device, "/shader/spv/culling.spv");
        try (MemoryStack stack = stackPush()) {
            VkPipelineShaderStageCreateInfo stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(compModule)
                    .pName(stack.UTF8("main"));

            VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0).size(108);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(bindlessDescriptorSetLayout))
                    .pPushConstantRanges(pushConstants);

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
            computePipelineLayout = pPipelineLayout.get(0);

            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .layout(computePipelineLayout)
                    .stage(stageInfo);

            LongBuffer pComputePipeline = stack.mallocLong(1);
            vkCreateComputePipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pComputePipeline);
            cullingPipeline = pComputePipeline.get(0);
        }
        vkDestroyShaderModule(device, compModule, null);
    }

    private static long loadShaderModule(VkDevice device, String resourcePath) {
        ByteBuffer shaderCode = readResourceToOffHeap(resourcePath);
        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(shaderCode);

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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void bindUbershader(VkCommandBuffer cmd) {
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, ubershaderPipeline);
        try (MemoryStack stack = stackPush()) {
            vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0, stack.longs(bindlessDescriptorSet), null);
        }
    }

    public static void bindComputeShader(VkCommandBuffer cmd) {
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, cullingPipeline);
        try (MemoryStack stack = stackPush()) {
            vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, computePipelineLayout, 0, stack.longs(bindlessDescriptorSet), null);
        }
    }

    public static void pushComputeData(VkCommandBuffer cmd, long matrixPtr, float camX, float camY, float camZ, float p11, float screenWidth, float screenHeight, float mipLevels, int trueInstanceOffset, int commandOffset, int snapshotIndex, float passType) {
        PUSH_CONSTANT_BUFFER.clear();
        MemoryUtil.memCopy(matrixPtr, MemoryUtil.memAddress(PUSH_CONSTANT_BUFFER), 16 * 4L);
        PUSH_CONSTANT_BUFFER.position(16);
        PUSH_CONSTANT_BUFFER.put(camX);
        PUSH_CONSTANT_BUFFER.put(camY);
        PUSH_CONSTANT_BUFFER.put(camZ);
        PUSH_CONSTANT_BUFFER.put(p11);
        PUSH_CONSTANT_BUFFER.put(screenWidth);
        PUSH_CONSTANT_BUFFER.put(screenHeight);
        PUSH_CONSTANT_BUFFER.put(mipLevels);
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(trueInstanceOffset));
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(commandOffset));
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(snapshotIndex));
        PUSH_CONSTANT_BUFFER.put(passType); // ---> NEW FLAG
        PUSH_CONSTANT_BUFFER.flip();
        vkCmdPushConstants(cmd, computePipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, PUSH_CONSTANT_BUFFER);
    }

    public static void pushGlobalData(VkCommandBuffer cmd, long matrixPtr) {
        PUSH_CONSTANT_BUFFER.clear();
        MemoryUtil.memCopy(matrixPtr, MemoryUtil.memAddress(PUSH_CONSTANT_BUFFER), 16 * 4L);
        PUSH_CONSTANT_BUFFER.position(16);
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(0)); // renderType
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.flip();
        vkCmdPushConstants(cmd, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, PUSH_CONSTANT_BUFFER);
    }

    public static void pushUIState(VkCommandBuffer cmd, long matrixPtr, int texId, int vertexOffset, float screenW, float screenH) {
        PUSH_CONSTANT_BUFFER.clear();
        MemoryUtil.memCopy(matrixPtr, MemoryUtil.memAddress(PUSH_CONSTANT_BUFFER), 16 * 4L);
        PUSH_CONSTANT_BUFFER.position(16);
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(1));
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(vertexOffset));
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(texId));
        PUSH_CONSTANT_BUFFER.put(screenW);
        PUSH_CONSTANT_BUFFER.put(screenH);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.flip();
        vkCmdPushConstants(cmd, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, PUSH_CONSTANT_BUFFER);
    }

    public static void pushGlobalData(VkCommandBuffer cmd, int currentInstanceOffset, long matrixPtr) {
        PUSH_CONSTANT_BUFFER.clear();
        MemoryUtil.memCopy(matrixPtr, MemoryUtil.memAddress(PUSH_CONSTANT_BUFFER), 16 * 4L);
        PUSH_CONSTANT_BUFFER.position(16);
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(0));
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(currentInstanceOffset));
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.put(0f);
        PUSH_CONSTANT_BUFFER.flip();
        vkCmdPushConstants(cmd, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, PUSH_CONSTANT_BUFFER);
    }

    public static void initHiZCompute(VkDevice device) {
        long compModule = loadShaderModule(device, "/shader/spv/hiz.spv");
        try (MemoryStack stack = stackPush()) {
            VkPipelineShaderStageCreateInfo stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(compModule)
                    .pName(stack.UTF8("main"));

            // 16 bytes for 2 ints and 2 floats
            VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0).size(20);

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(bindlessDescriptorSetLayout))
                    .pPushConstantRanges(pushConstants);

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
            hizDownsamplePipelineLayout = pPipelineLayout.get(0);

            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                    .layout(hizDownsamplePipelineLayout)
                    .stage(stageInfo);

            LongBuffer pComputePipeline = stack.mallocLong(1);
            vkCreateComputePipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pComputePipeline);
            hizDownsamplePipeline = pComputePipeline.get(0);
        }
        vkDestroyShaderModule(device, compModule, null);
    }

    public static void updateHiZMipDescriptor(int mipLevel, long imageView, long sampler) {
        try (MemoryStack stack = stackPush()) {
            // 1. Bind as a STORAGE IMAGE for Writing (Binding 11)
            VkDescriptorImageInfo.Buffer storageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .imageView(imageView);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
            writes.get(0).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(11).dstArrayElement(mipLevel)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1)
                    .pImageInfo(storageInfo);

            // 2. Bind as a COMBINED SAMPLER for Reading (Binding 0). Offset ID by 4000!
            VkDescriptorImageInfo.Buffer samplerInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .imageView(imageView).sampler(sampler);

            writes.get(1).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(0).dstArrayElement(4000 + mipLevel)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
                    .pImageInfo(samplerInfo);

            vkUpdateDescriptorSets(VulkanContext.getDevice(), writes, null);
        }
    }

    // Push constants specifically for the downsampler shader
    // Push constants specifically for the downsampler shader
    public static void pushHiZDownsampleData(VkCommandBuffer cmd, int inputTextureId, int outputImageId, float invWidth, float invHeight, int isFirstPass) {
        PUSH_CONSTANT_BUFFER.clear();
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(inputTextureId)); // Cast raw bits!
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(outputImageId));  // Cast raw bits!
        PUSH_CONSTANT_BUFFER.put(invWidth);
        PUSH_CONSTANT_BUFFER.put(invHeight);
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(isFirstPass));    // Cast raw bits!
        PUSH_CONSTANT_BUFFER.flip();
        vkCmdPushConstants(cmd, hizDownsamplePipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, PUSH_CONSTANT_BUFFER);
    }

    // Push constants specifically for the depth pre-pass
    public static void pushDepthGlobalData(VkCommandBuffer cmd, long matrixPtr) {
        PUSH_CONSTANT_BUFFER.clear();
        MemoryUtil.memCopy(matrixPtr, MemoryUtil.memAddress(PUSH_CONSTANT_BUFFER), 16 * 4L);
        PUSH_CONSTANT_BUFFER.position(16);
        PUSH_CONSTANT_BUFFER.put(Float.intBitsToFloat(0));
        PUSH_CONSTANT_BUFFER.flip();
        vkCmdPushConstants(cmd, hizPipelineLayout, VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT, 0, PUSH_CONSTANT_BUFFER);
    }

    public static void updatePrepassDepthDescriptor(long imageView, long sampler) {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorImageInfo.Buffer samplerInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL)
                    .imageView(imageView).sampler(sampler);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(1, stack);
            writes.get(0).sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(bindlessDescriptorSet).dstBinding(0).dstArrayElement(3999)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(1)
                    .pImageInfo(samplerInfo);

            vkUpdateDescriptorSets(VulkanContext.getDevice(), writes, null);
        }
    }
}