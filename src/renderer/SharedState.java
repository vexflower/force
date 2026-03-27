package renderer;

import java.util.concurrent.atomic.AtomicReference;

public class SharedState {

    // Pre-allocate both buffers. Zero GC forever.
    private final RenderState stateA = new RenderState();
    private final RenderState stateB = new RenderState();

    // The atomic pointer. Defaults to stateA.
    private final AtomicReference<RenderState> frontBuffer = new AtomicReference<>(stateA);

    /**
     * Called by the RENDER THREAD.
     * Grabs whichever state is currently "live". Never blocks.
     */
    public RenderState getFrontBuffer() {
        return frontBuffer.get();
    }

    /**
     * Called by the LOGIC THREAD.
     * Grabs whichever state is NOT currently being read by the GPU.
     */
    public RenderState getBackBuffer() {
        return frontBuffer.get() == stateA ? stateB : stateA;
    }

    /**
     * Called by the LOGIC THREAD at the end of a tick.
     * Instantly swaps the pointers. The next time the Render Thread loops,
     * it will seamlessly pick up the new state.
     */
    public void swap() {
        frontBuffer.set(getBackBuffer());
    }
}