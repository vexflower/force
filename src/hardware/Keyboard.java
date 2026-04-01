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
         * @param action PRESS, RELEASE, or REPEAT
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
            keys[key] = (action != 0);
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

    public static final int
            SPACE         = 32,
            APOSTROPHE    = 39,
            COMMA         = 44,
            MINUS         = 45,
            PERIOD        = 46,
            SLASH         = 47,
            ZERO             = 48,
            ONE             = 49,
            TWO             = 50,
            THREE           = 51,
            FOUR         = 52,
            FIVE          = 53,
            SIX          = 54,
            SEVEN           = 55,
            EIGHT           = 56,
            NINE           = 57,
            SEMICOLON     = 59,
            EQUAL         = 61,
            A             = 65,
            B             = 66,
            C             = 67,
            D             = 68,
            E             = 69,
            F             = 70,
            G             = 71,
            H             = 72,
            I             = 73,
            J             = 74,
            K             = 75,
            L             = 76,
            M             = 77,
            N             = 78,
            O             = 79,
            P             = 80,
            Q             = 81,
            R             = 82,
            S             = 83,
            T             = 84,
            U             = 85,
            V             = 86,
            W             = 87,
            X             = 88,
            Y             = 89,
            Z             = 90,
            LEFT_BRACKET  = 91,
            BACKSLASH     = 92,
            RIGHT_BRACKET = 93,
            GRAVE_ACCENT  = 96,
            WORLD_1       = 161,
            WORLD_2       = 162;

    public static final int
            ESCAPE        = 256,
            ENTER         = 257,
            TAB           = 258,
            BACKSPACE     = 259,
            INSERT        = 260,
            DELETE        = 261,
            RIGHT         = 262,
            LEFT          = 263,
            DOWN          = 264,
            UP            = 265,
            PAGE_UP       = 266,
            PAGE_DOWN     = 267,
            HOME          = 268,
            END           = 269,
            CAPS_LOCK     = 280,
            SCROLL_LOCK   = 281,
            NUM_LOCK      = 282,
            PRINT_SCREEN  = 283,
            PAUSE         = 284,
            F1            = 290,
            F2            = 291,
            F3            = 292,
            F4            = 293,
            F5            = 294,
            F6            = 295,
            F7            = 296,
            F8            = 297,
            F9            = 298,
            F10           = 299,
            F11           = 300,
            F12           = 301,
            F13           = 302,
            F14           = 303,
            F15           = 304,
            F16           = 305,
            F17           = 306,
            F18           = 307,
            F19           = 308,
            F20           = 309,
            F21           = 310,
            F22           = 311,
            F23           = 312,
            F24           = 313,
            F25           = 314,
            KP_0          = 320,
            KP_1          = 321,
            KP_2          = 322,
            KP_3          = 323,
            KP_4          = 324,
            KP_5          = 325,
            KP_6          = 326,
            KP_7          = 327,
            KP_8          = 328,
            KP_9          = 329,
            KP_DECIMAL    = 330,
            KP_DIVIDE     = 331,
            KP_MULTIPLY   = 332,
            KP_SUBTRACT   = 333,
            KP_ADD        = 334,
            KP_ENTER      = 335,
            KP_EQUAL      = 336,
            LEFT_SHIFT    = 340,
            LEFT_CONTROL  = 341,
            LEFT_ALT      = 342,
            LEFT_SUPER    = 343,
            RIGHT_SHIFT   = 344,
            RIGHT_CONTROL = 345,
            RIGHT_ALT     = 346,
            RIGHT_SUPER   = 347,
            MENU          = 348,
            LAST          = MENU;

    public static final int
            MOD_SHIFT     = 0x1,
            MOD_CONTROL   = 0x2,
            MOD_ALT       = 0x4,
            MOD_SUPER     = 0x8,
            MOD_CAPS_LOCK = 0x10,
            MOD_NUM_LOCK  = 0x20;

    public static final int
            MOUSE_BUTTON_1      = 0,
            MOUSE_BUTTON_2      = 1,
            MOUSE_BUTTON_3      = 2,
            MOUSE_BUTTON_4      = 3,
            MOUSE_BUTTON_5      = 4,
            MOUSE_BUTTON_6      = 5,
            MOUSE_BUTTON_7      = 6,
            MOUSE_BUTTON_8      = 7,
            MOUSE_BUTTON_LAST   = MOUSE_BUTTON_8,
            MOUSE_BUTTON_LEFT   = MOUSE_BUTTON_1,
            MOUSE_BUTTON_RIGHT  = MOUSE_BUTTON_2,
            MOUSE_BUTTON_MIDDLE = MOUSE_BUTTON_3;

    public static final int
            JOYSTICK_1    = 0,
            JOYSTICK_2    = 1,
            JOYSTICK_3    = 2,
            JOYSTICK_4    = 3,
            JOYSTICK_5    = 4,
            JOYSTICK_6    = 5,
            JOYSTICK_7    = 6,
            JOYSTICK_8    = 7,
            JOYSTICK_9    = 8,
            JOYSTICK_10   = 9,
            JOYSTICK_11   = 10,
            JOYSTICK_12   = 11,
            JOYSTICK_13   = 12,
            JOYSTICK_14   = 13,
            JOYSTICK_15   = 14,
            JOYSTICK_16   = 15,
            JOYSTICK_LAST = JOYSTICK_16;

    public static final int
            GAMEPAD_BUTTON_A            = 0,
            GAMEPAD_BUTTON_B            = 1,
            GAMEPAD_BUTTON_X            = 2,
            GAMEPAD_BUTTON_Y            = 3,
            GAMEPAD_BUTTON_LEFT_BUMPER  = 4,
            GAMEPAD_BUTTON_RIGHT_BUMPER = 5,
            GAMEPAD_BUTTON_BACK         = 6,
            GAMEPAD_BUTTON_START        = 7,
            GAMEPAD_BUTTON_GUIDE        = 8,
            GAMEPAD_BUTTON_LEFT_THUMB   = 9,
            GAMEPAD_BUTTON_RIGHT_THUMB  = 10,
            GAMEPAD_BUTTON_DPAD_UP      = 11,
            GAMEPAD_BUTTON_DPAD_RIGHT   = 12,
            GAMEPAD_BUTTON_DPAD_DOWN    = 13,
            GAMEPAD_BUTTON_DPAD_LEFT    = 14,
            GAMEPAD_BUTTON_LAST         = GAMEPAD_BUTTON_DPAD_LEFT,
            GAMEPAD_BUTTON_CROSS        = GAMEPAD_BUTTON_A,
            GAMEPAD_BUTTON_CIRCLE       = GAMEPAD_BUTTON_B,
            GAMEPAD_BUTTON_SQUARE       = GAMEPAD_BUTTON_X,
            GAMEPAD_BUTTON_TRIANGLE     = GAMEPAD_BUTTON_Y;

    public static final int
            GAMEPAD_AXIS_LEFT_X        = 0,
            GAMEPAD_AXIS_LEFT_Y        = 1,
            GAMEPAD_AXIS_RIGHT_X       = 2,
            GAMEPAD_AXIS_RIGHT_Y       = 3,
            GAMEPAD_AXIS_LEFT_TRIGGER  = 4,
            GAMEPAD_AXIS_RIGHT_TRIGGER = 5,
            GAMEPAD_AXIS_LAST          = GAMEPAD_AXIS_RIGHT_TRIGGER;

    public static final int
            NO_ERROR              = 0,
            NOT_INITIALIZED       = 0x10001,
            NO_CURRENT_CONTEXT    = 0x10002,
            INVALID_ENUM          = 0x10003,
            INVALID_VALUE         = 0x10004,
            OUT_OF_MEMORY         = 0x10005,
            API_UNAVAILABLE       = 0x10006,
            VERSION_UNAVAILABLE   = 0x10007,
            PLATFORM_ERROR        = 0x10008,
            FORMAT_UNAVAILABLE    = 0x10009,
            NO_WINDOW_CONTEXT     = 0x1000A,
            CURSOR_UNAVAILABLE    = 0x1000B,
            FEATURE_UNAVAILABLE   = 0x1000C,
            FEATURE_UNIMPLEMENTED = 0x1000D,
            PLATFORM_UNAVAILABLE  = 0x1000E;

    public static final int
            FOCUSED                 = 0x20001,
            ICONIFIED               = 0x20002,
            RESIZABLE               = 0x20003,
            VISIBLE                 = 0x20004,
            DECORATED               = 0x20005,
            AUTO_ICONIFY            = 0x20006,
            FLOATING                = 0x20007,
            MAXIMIZED               = 0x20008,
            CENTER_CURSOR           = 0x20009,
            TRANSPARENT_FRAMEBUFFER = 0x2000A,
            HOVERED                 = 0x2000B,
            FOCUS_ON_SHOW           = 0x2000C,
            MOUSE_PASSTHROUGH       = 0x2000D,
            POSITION_X              = 0x2000E,
            POSITION_Y              = 0x2000F,
            SOFT_FULLSCREEN         = 0x20010;

    public static final int
            CURSOR                  = 0x33001,
            STICKY_KEYS             = 0x33002,
            STICKY_MOUSE_BUTTONS    = 0x33003,
            LOCK_KEY_MODS           = 0x33004,
            RAW_MOUSE_MOTION        = 0x33005,
            UNLIMITED_MOUSE_BUTTONS = 0x33006,
            IME                     = 0x33007;

    public static final int
            CURSOR_NORMAL   = 0x34001,
            CURSOR_HIDDEN   = 0x34002,
            CURSOR_DISABLED = 0x34003,
            CURSOR_CAPTURED = 0x34004;

    public static final int
            ARROW_CURSOR         = 0x36001,
            IBEAM_CURSOR         = 0x36002,
            CROSSHAIR_CURSOR     = 0x36003,
            POINTING_HAND_CURSOR = 0x36004,
            RESIZE_EW_CURSOR     = 0x36005,
            RESIZE_NS_CURSOR     = 0x36006,
            RESIZE_NWSE_CURSOR   = 0x36007,
            RESIZE_NESW_CURSOR   = 0x36008,
            RESIZE_ALL_CURSOR    = 0x36009,
            NOT_ALLOWED_CURSOR   = 0x3600A,
            HRESIZE_CURSOR       = RESIZE_EW_CURSOR,
            VRESIZE_CURSOR       = RESIZE_NS_CURSOR,
            HAND_CURSOR          = POINTING_HAND_CURSOR;
}