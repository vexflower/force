package shader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.shaderc.Shaderc.*;

public class SpirVCompiler {

    // ---> UPDATED TO MATCH YOUR SCREENSHOT <---
    private static final String SOURCE_DIR = "src/shader/code/";
    private static final String OUTPUT_DIR = "src/shader/spv/";

    public static void main(String[] args) {
        System.out.println("--- Starting SPIR-V Asset Compilation ---");

        long compiler = shaderc_compiler_initialize();
        if (compiler == NULL) throw new RuntimeException("Failed to initialize shaderc compiler");

        long options = shaderc_compile_options_initialize();
        shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);

        // 1. Compile Graphics Shaders
        compileShader(compiler, options, "vertex.vert", "vertex.spv", shaderc_glsl_vertex_shader);
        compileShader(compiler, options, "fragment.frag", "fragment.spv", shaderc_glsl_fragment_shader);

        // 2. Compile the new Compute Shader Brain!
        compileShader(compiler, options, "culling.comp", "culling.spv", shaderc_glsl_compute_shader);
        compileShader(compiler, options, "hiz.comp", "hiz.spv", shaderc_glsl_compute_shader);

        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);

        System.out.println("--- Compilation Complete ---");
    }

    private static void compileShader(long compiler, long options, String inputPath, String outputPath, int shaderKind) {
        File sourceFile = new File(SOURCE_DIR + inputPath);
        if (!sourceFile.exists()) {
            System.err.println("Skipping: Cannot find " + sourceFile.getAbsolutePath());
            return;
        }

        try {
            String source = Files.readString(sourceFile.toPath());
            long result = shaderc_compile_into_spv(compiler, source, shaderKind, sourceFile.getName(), "main", options);

            if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                throw new RuntimeException("Failed to compile " + sourceFile.getName() + ":\n" + shaderc_result_get_error_message(result));
            }

            ByteBuffer spv = shaderc_result_get_bytes(result);

            File outputFile = new File(OUTPUT_DIR + outputPath);
            outputFile.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 FileChannel channel = fos.getChannel()) {
                channel.write(spv);
            }

            System.out.println("Successfully compiled: " + outputFile.getName());
            shaderc_result_release(result);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}