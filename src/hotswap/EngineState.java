package hotswap;

import java.util.HashMap;
import java.util.Map;

/**
 * The generic data container that survives a Level 4 Engine Hotswap.
 * It lives in the Bootloader, so it is never unloaded from RAM.
 */
public class EngineState {

    // We use a generic map so the Bootloader doesn't need to know about specific Game classes.
    public Map<String, Object> persistentData = new HashMap<>();

    public EngineState() {
        // Empty state for the very first time the game boots
    }
}