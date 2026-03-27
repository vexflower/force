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
        protected long pipelineLayout;
        protected long graphicsPipeline;

        protected int matrixOffset;
        protected int materialOffset;

        public ShaderPipeline(VkDevice device, long renderPass, String vertName, String fragName) {
            // Load the pre-compiled .spv binaries from the classpath (Fat-Jar safe!)
            long vertModule = loadShaderModule(device, "/shader/vertex/" + vertName + ".spv");
            long fragModule = loadShaderModule(device, "/shader/fragment/" + fragName + ".spv");

            // 2. We will build the actual Graphics Pipeline here in the next step!
            // This is where Vulkan gets told how to draw the triangle.

            // 3. Cleanup the raw modules from RAM
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

        vkCmdPushConstants(
                commandBuffer,
                currentPipeline.pipelineLayout,
                VK_SHADER_STAGE_VERTEX_BIT,
                byteOffset,
                PUSH_CONSTANT_BUFFER
        );
    }

    // =========================================================================================
    // FAT-JAR OFF-HEAP RESOURCE LOADER
    // =========================================================================================

    private static long loadShaderModule(VkDevice device, String resourcePath) {
        ByteBuffer shaderCode = readResourceToOffHeap(resourcePath);

        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(shaderCode); // Hand Vulkan the off-heap buffer

            LongBuffer pShaderModule = stack.mallocLong(1);
            if (vkCreateShaderModule(device, createInfo, null, pShaderModule) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan shader module from: " + resourcePath);
            }

            // Memory must be explicitly freed once Vulkan has ingested it
            MemoryUtil.memFree(shaderCode);

            return pShaderModule.get(0);
        }
    }

    /**
     * Streams a binary file from inside a .jar directly into an off-heap C-buffer.
     * ZERO Heap allocations for the file bytes.
     */
    private static ByteBuffer readResourceToOffHeap(String resourcePath) {
        try (InputStream stream = VKShader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new RuntimeException("SPIR-V Shader not found in classpath: " + resourcePath);
            }

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

    // =========================================================================================
    // THE ENTITY SHADER PIPELINE
    // =========================================================================================

    public static class EntityShaderPipeline extends ShaderPipeline {

        public static EntityShaderPipeline pipeline;

        public EntityShaderPipeline(VkDevice device, long renderPass) {
            // Automatically appends .spv in the loader
            super(device, renderPass, "entity", "entity");
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