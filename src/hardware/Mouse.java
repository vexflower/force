package hardware;

import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.glfwGetTime;

/**
 * High-precision, event-driven Mouse hardware interface.
 */
public final class Mouse {

    // Listener Interfaces for instant input responses
    public interface MouseButtonListener {
        void onMouseButton(int button, int action, double timestamp);
    }

    public interface MouseMoveListener {
        void onMouseMove(double x, double y, double dx, double dy, double timestamp);
    }

    public interface MouseScrollListener {
        void onMouseScroll(double offsetX, double offsetY, double timestamp);
    }

    private static final List<MouseButtonListener> buttonListeners = new CopyOnWriteArrayList<>();
    private static final List<MouseMoveListener> moveListeners = new CopyOnWriteArrayList<>();
    private static final List<MouseScrollListener> scrollListeners = new CopyOnWriteArrayList<>();

    // Polling State Data
    private static final boolean[] buttons = new boolean[8];
    private static double mouseX, mouseY;
    private static double lastX, lastY;
    private static double scrollX, scrollY;
    private static boolean isFirstMove = true;

    // Callbacks
    private final GLFWCursorPosCallback cursorPosCallback;
    private final GLFWMouseButtonCallback mouseButtonCallback;
    private final GLFWScrollCallback scrollCallback;

    public static final int LEFT = 0, RIGHT = 1, MIDDLE = 2;

    public Mouse() {
        mouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                double time = glfwGetTime();
                if (button >= 0 && button < buttons.length) {
                    buttons[button] = (action != GLFW_RELEASE);
                }
                for (MouseButtonListener l : buttonListeners) {
                    l.onMouseButton(button, action, time);
                }
            }
        };

        cursorPosCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xPos, double yPos) {
                double time = glfwGetTime();

                if (isFirstMove) {
                    lastX = xPos;
                    lastY = yPos;
                    isFirstMove = false;
                }

                double dx = xPos - lastX;
                double dy = yPos - lastY;

                mouseX = xPos;
                mouseY = yPos;
                lastX = xPos;
                lastY = yPos;

                for (MouseMoveListener l : moveListeners) {
                    l.onMouseMove(xPos, yPos, dx, dy, time);
                }
            }
        };

        scrollCallback = new GLFWScrollCallback() {
            @Override
            public void invoke(long window, double offsetX, double offsetY) {
                double time = glfwGetTime();
                scrollX = offsetX;
                scrollY = offsetY;

                for (MouseScrollListener l : scrollListeners) {
                    l.onMouseScroll(offsetX, offsetY, time);
                }
            }
        };
    }

    public static void addListener(MouseButtonListener l) { buttonListeners.add(l); }
    public static void addListener(MouseMoveListener l) { moveListeners.add(l); }
    public static void addListener(MouseScrollListener l) { scrollListeners.add(l); }

    public static boolean isButtonDown(int button) {
        if (button < 0 || button >= buttons.length) return false;
        return buttons[button];
    }

    public static boolean isAnyButtonDown() {
        for (boolean b : buttons) if (b) return true;
        return false;
    }

    public GLFWCursorPosCallback getMouseMoveCallback() { return cursorPosCallback; }
    public GLFWMouseButtonCallback getMouseButtonsCallback() { return mouseButtonCallback; }
    public GLFWScrollCallback getMouseScrollCallback() { return scrollCallback; }

    public void destroy() {
        cursorPosCallback.free();
        mouseButtonCallback.free();
        scrollCallback.free();
    }

    public static float getX() { return (float) mouseX; }
    public static float getY() { return (float) mouseY; }
    public static float getScrollX() { return (float) scrollX; }
    public static float getScrollY() { return (float) scrollY; }

    /** Must be called by Display.update() to clear per-frame delta states */
    public static void endFrame() {
        scrollX = 0;
        scrollY = 0;
        // DX/DY are handled by lastX/lastY in the callback inherently now
    }
}