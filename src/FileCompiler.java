import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FileCompiler {

    public static void main(String[] args) {
        // Point this to your project's source directory
        Path sourceDir = Paths.get("src");
        Path outputFile = Paths.get("code.txt");

        System.out.println("Scanning directory: " + sourceDir.toAbsolutePath());

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile);
             Stream<Path> paths = Files.walk(sourceDir)) {

            paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.toString().toLowerCase();
                        String fullPath = path.toString();

                        // 1. Target specifically Java and Shader files
                        boolean isTargetFile = fileName.endsWith(".java") ||
                                fileName.endsWith(".vert") ||
                                fileName.endsWith(".frag");

                        // 2. Explicitly EXCLUDE anything inside a "resources" directory
                        // Checking both slash types for Windows and macOS/Linux compatibility
                        boolean inResourcesDir = fullPath.contains("\\resources\\") ||
                                fullPath.contains("/resources/") ||
                                fullPath.contains("\\resources") || // Edge case for root-level match
                                fullPath.contains("/resources");

                        return isTargetFile && !inResourcesDir;
                    })
                    .forEach(path -> {
                        try {
                            // Create a clear visual boundary for the LLM to parse
                            writer.write("{\n");
                            writer.write("type: uploaded file\n");
                            writer.write("fileName: " + path.toString().replace("\\", "/") + "\n");
                            writer.write("fullContent:\n");

                            // Read the file and append it to code.txt
                            Files.readAllLines(path).forEach(line -> {
                                try {
                                    writer.write(line + "\n");
                                } catch (IOException e) {
                                    System.err.println("Failed to write line for: " + path.toString());
                                }
                            });

                            writer.write("}\n\n");
                            System.out.println("Appended: " + path.getFileName());

                        } catch (IOException e) {
                            System.err.println("Could not read file: " + path.toString());
                        }
                    });

            System.out.println("\nSUCCESS! All codebase files compiled into -> " + outputFile.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Critical Error walking the directory tree: " + e.getMessage());
        }
    }
}