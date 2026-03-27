package shader;

import entity.Light;
import lang.Mat4;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAllocFloat;
import static org.lwjgl.vulkan.VK10.*;

/**
 * AAA Vulkan Shader Manager.
 * Fully Fat-Jar compatible. Loads pre-compiled SPIR-V binaries directly into off-heap memory.
 */
public final class VKShader {

    private static ShaderPipeline currentPipeline;

    // 16 floats = 64 bytes. Kept off-heap for zero-allocation Matrix pushing.
    private static final FloatBuffer PUSH_CONSTANT_BUFFER = memAllocFloat(16);

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

                shaderStages.get(0).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
                shaderStages.get(0).stage(VK_SHADER_STAGE_VERTEX_BIT);
                shaderStages.get(0).module(vertModule);
                shaderStages.get(0).pName(stack.UTF8("main"));

                shaderStages.get(1).sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);
                shaderStages.get(1).stage(VK_SHADER_STAGE_FRAGMENT_BIT);
                shaderStages.get(1).module(fragModule);
                shaderStages.get(1).pName(stack.UTF8("main"));

                // 2. Vertex Input (EMPTY for now! We are hardcoding the triangle in the shader)
                VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack);
                vertexInputInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

                // 3. Input Assembly (Triangles)
                VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
                inputAssembly.sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO);
                inputAssembly.topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                inputAssembly.primitiveRestartEnable(false);

                // 4. Viewport & Scissor
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
                viewport.x(0.0f).y(0.0f).width(extent.width()).height(extent.height()).minDepth(0.0f).maxDepth(1.0f);

                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.offset(VkOffset2D.calloc(stack).set(0, 0)).extent(extent);

                VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack);
                viewportState.sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);
                viewportState.pViewports(viewport);
                viewportState.pScissors(scissor);

                // 5. Rasterizer
                VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack);
                rasterizer.sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO);
                rasterizer.depthClampEnable(false);
                rasterizer.rasterizerDiscardEnable(false);
                rasterizer.polygonMode(VK_POLYGON_MODE_FILL);
                rasterizer.lineWidth(1.0f);

                // CRITICAL FIX: Disable culling so we can see both sides of the spinning triangle!
                rasterizer.cullMode(VK_CULL_MODE_NONE);
                rasterizer.frontFace(VK_FRONT_FACE_CLOCKWISE);

                // 6. Multisampling
                VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack);
                multisampling.sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO);
                multisampling.sampleShadingEnable(false);
                multisampling.rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

                // 7. Color Blending
                VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
                colorBlendAttachment.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
                colorBlendAttachment.blendEnable(false);

                VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack);
                colorBlending.sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO);
                colorBlending.logicOpEnable(false);
                colorBlending.pAttachments(colorBlendAttachment);

                // 8. Pipeline Layout (Push Constants setup)
                VkPushConstantRange.Buffer pushConstants = VkPushConstantRange.calloc(1, stack);
                pushConstants.stageFlags(VK_SHADER_STAGE_VERTEX_BIT);
                pushConstants.offset(0);
                pushConstants.size(64); // 64 bytes = exactly one 16-float Mat4

                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack);
                pipelineLayoutInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
                pipelineLayoutInfo.pPushConstantRanges(pushConstants);

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

    public static class EntityShaderPipeline extends ShaderPipeline {
        public static EntityShaderPipeline pipeline = null;

        public EntityShaderPipeline(VkDevice device, long renderPass, VkExtent2D extent) {
            super(device, renderPass, extent, "vertex", "fragment");
        }

        @Override
        protected void defineByteOffsets() {
            this.matrixOffset = 0;
            this.materialOffset = 64;
        }

        public static void loadTransformationMatrix(VkCommandBuffer commandBuffer, Mat4 matrix) {
            loadMatrix(commandBuffer, pipeline.matrixOffset, matrix);
        }

        public static void bindLights(VkCommandBuffer commandBuffer, List<Light> lights) {
            // Descriptor sets will go here
        }
    }
}