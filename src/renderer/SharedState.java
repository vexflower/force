package renderer;

public class SharedState {

    // Triple Buffering: Zero GC forever.
    private final RenderState[] states = new RenderState[] {
            new RenderState(0), new RenderState(1), new RenderState(2)
    };

    private int readIndex = 0;
    private int writeIndex = 1;
    private int readyIndex = -1;

    public synchronized RenderState getFrontBuffer() {
        // ONLY swap if the Logic Thread has flagged a new frame as ready!
        if (readyIndex != -1) {
            readIndex = readyIndex;
            readyIndex = -1; // <--- THE FIX: Mark the buffer as fully consumed!
        }
        return states[readIndex];
    }

    public synchronized RenderState getBackBuffer() {
        return states[writeIndex];
    }

    public synchronized void swap() {
        int oldReady = readyIndex;
        readyIndex = writeIndex;

        if (oldReady != -1) {
            writeIndex = oldReady;
        } else {
            // Find a free buffer that is NOT the readIndex AND NOT the readyIndex
            for (int i = 0; i < 3; i++) {
                if (i != readIndex && i != readyIndex) {
                    writeIndex = i;
                    break;
                }
            }
        }
    }
}