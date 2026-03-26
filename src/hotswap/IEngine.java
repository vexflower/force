package hotswap;

/**
 * The Bootloader only knows about this interface.
 * It knows nothing about LWJGL, Vectors, or Shaders.
 */
public interface IEngine {
    void start(EngineState state);
    EngineState stopAndExtractState();
    boolean isRunning();
}