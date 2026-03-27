package shader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.shaderc.Shaderc.*;

/**
 * Standalone Asset Pipeline Tool.
 * Run this class's main() method to compile all .vert and .frag files into .spv binaries.
 */
public class SpirVCompiler {

    // Define where your human-readable shaders live, and where the binaries should go
    private static final String SOURCE_DIR = "src/shader/";
    private static final String OUTPUT_DIR = "src/shader/"; // Could also be a separate 'bin' folder

    static void main(String[] args) {
        System.out.println("--- Starting SPIR-V Asset Compilation ---");

        long compiler = shaderc_compiler_initialize();
        if (compiler == NULL) throw new RuntimeException("Failed to initialize shaderc compiler");

        long options = shaderc_compile_options_initialize();
        shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance);

        // Compile Vertex Shaders
        compileShader(compiler, options, "vertex/entity.vert", shaderc_glsl_vertex_shader);

        // Compile Fragment Shaders
        compileShader(compiler, options, "fragment/entity.frag", shaderc_glsl_fragment_shader);

        shaderc_compile_options_release(options);
        shaderc_compiler_release(compiler);

        System.out.println("--- Compilation Complete ---");
    }

    private static void compileShader(long compiler, long options, String relativePath, int shaderKind) {
        File sourceFile = new File(SOURCE_DIR + relativePath);
        if (!sourceFile.exists()) {
            System.err.println("Skipping: Cannot find " + sourceFile.getAbsolutePath());
            return;
        }

        try {
            // 1. Read the raw GLSL text
            String source = Files.readString(sourceFile.toPath());

            // 2. Compile to SPIR-V
            long result = shaderc_compile_into_spv(compiler, source, shaderKind, sourceFile.getName(), "main", options);

            if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                throw new RuntimeException("Failed to compile " + sourceFile.getName() + ":\n" + shaderc_result_get_error_message(result));
            }

            // 3. Extract the bytecode
            ByteBuffer spv = shaderc_result_get_bytes(result);

            // 4. Save to a .spv file
            String outputPath = OUTPUT_DIR + relativePath.replace(".vert", ".spv").replace(".frag", ".spv");
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs(); // Ensure directories exist

            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 FileChannel channel = fos.getChannel()) {
                channel.write(spv);
            }

            System.out.println("Successfully compiled: " + outputFile.getName());

            // 5. Clean up C++ memory
            shaderc_result_release(result);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}