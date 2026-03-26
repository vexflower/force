package hardware;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.glfw.GLFW.*;

/**
 * High-precision, event-driven Keyboard hardware interface.
 */
public final class Keyboard extends GLFWKeyCallback {

    // Standard Polling Array (Kept for legacy/simple checks like "Is W held down?")
    private static final boolean[] keys = new boolean[348];

    // Event-Driven Listeners for frame-independent accuracy (Zero-Allocation)
    private static final List<KeyListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Interface for receiving instantaneous, timestamped key events.
     * Use this for rhythm games, typing interfaces, or fighting game combos.
     */
    public interface KeyListener {
        /**
         * @param key The GLFW Key Code
         * @param action GLFW_PRESS, GLFW_RELEASE, or GLFW_REPEAT
         * @param timestamp The exact sub-millisecond time the OS registered the event
         */
        void onKeyEvent(int key, int action, double timestamp);
    }

    public static void addKeyListener(KeyListener listener) {
        listeners.add(listener);
    }

    public static void removeKeyListener(KeyListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void invoke(long window, int key, int scancode, int action, int mods) {
        // 1. Capture the exact microsecond this hardware interrupt occurred
        double exactTime = glfwGetTime();

        // GLFW sometimes returns -1 for unmapped keys; ignore them to prevent array crashes
        if (key >= 0 && key < keys.length) {
            keys[key] = (action != GLFW.GLFW_RELEASE);
        }

        // 2. Instantly notify all listeners with primitive data (Zero Allocation)
        for (KeyListener listener : listeners) {
            listener.onKeyEvent(key, action, exactTime);
        }
    }

    public static boolean isKeyDown(int keycode) {
        if (keycode < 0 || keycode >= keys.length) return false;
        return keys[keycode];
    }

    public static boolean areKeysDown(int... keysToCheck) {
        for (int k : keysToCheck) {
            if (keys[k]) return true;
        }
        return false;
    }

    public static boolean areAllKeysDown(int... keysToCheck) {
        for (int k : keysToCheck) {
            if (!keys[k]) return false;
        }
        return true;
    }

    // GLFW Key Constants
    public static final int
            SPACE         = GLFW_KEY_SPACE,
            APOSTROPHE    = GLFW_KEY_APOSTROPHE,
            COMMA         = GLFW_KEY_COMMA,
            MINUS         = GLFW_KEY_MINUS,
            PERIOD        = GLFW_KEY_PERIOD,
            SLASH         = GLFW_KEY_SLASH,
            ZERO          = GLFW_KEY_0,
            ONE           = GLFW_KEY_1,
            TWO           = GLFW_KEY_2,
            THREE         = GLFW_KEY_3,
            FOUR          = GLFW_KEY_4,
            FIVE          = GLFW_KEY_5,
            SIX           = GLFW_KEY_6,
            SEVEN         = GLFW_KEY_7,
            EIGHT         = GLFW_KEY_8,
            NINE          = GLFW_KEY_9,
            SEMICOLON     = GLFW_KEY_SEMICOLON,
            EQUAL         = GLFW_KEY_EQUAL,
            A             = GLFW_KEY_A,
            B             = GLFW_KEY_B,
            C             = GLFW_KEY_C,
            D             = GLFW_KEY_D,
            E             = GLFW_KEY_E,
            F             = GLFW_KEY_F,
            G             = GLFW_KEY_G,
            H             = GLFW_KEY_H,
            I             = GLFW_KEY_I,
            J             = GLFW_KEY_J,
            K             = GLFW_KEY_K,
            L             = GLFW_KEY_L,
            M             = GLFW_KEY_M,
            N             = GLFW_KEY_N,
            O             = GLFW_KEY_O,
            P             = GLFW_KEY_P,
            Q             = GLFW_KEY_Q,
            R             = GLFW_KEY_R,
            S             = GLFW_KEY_S,
            T             = GLFW_KEY_T,
            U             = GLFW_KEY_U,
            V             = GLFW_KEY_V,
            W             = GLFW_KEY_W,
            X             = GLFW_KEY_X,
            Y             = GLFW_KEY_Y,
            Z             = GLFW_KEY_Z,
            ESCAPE        = GLFW_KEY_ESCAPE,
            ENTER         = GLFW_KEY_ENTER,
            TAB           = GLFW_KEY_TAB,
            BACKSPACE     = GLFW_KEY_BACKSPACE,
            RIGHT         = GLFW_KEY_RIGHT,
            LEFT          = GLFW_KEY_LEFT,
            DOWN          = GLFW_KEY_DOWN,
            UP            = GLFW_KEY_UP,
            LEFT_SHIFT    = GLFW_KEY_LEFT_SHIFT,
            LEFT_CONTROL  = GLFW_KEY_LEFT_CONTROL,
            LEFT_ALT      = GLFW_KEY_LEFT_ALT;
}