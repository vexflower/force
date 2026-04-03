package hardware;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Window {
    private static int width = 1280;
    private static int height = 720;
    private static long handle;
    private static final String TITLE = "Force Engine";

    private static int frames;
    private static long lastFPSTime;
    private static double lastFrameTime;
    private static double deltaInSeconds;

    public static boolean wasResized = false;
    private static boolean showFPSTitle = true;

    private static Keyboard keyboard;
    private static Mouse mouse;
    private static GLFWWindowSizeCallback windowSizeCallback;

    // We keep the root UI container here temporarily until Phase 2
    private static ui.Container contentPane;

    public static void create(int w, int h) {
        if (!glfwInit()) throw new RuntimeException("ERROR: GLFW wasn't initialized");
        width = (w == 0) ? 1280 : w;
        height = (h == 0) ? 720 : h;

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API); // Tell GLFW to NOT use OpenGL
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        handle = glfwCreateWindow(width, height, TITLE, NULL, NULL);
        if (handle == NULL) throw new RuntimeException("Failed to create GLFW window");

        // Center on screen
        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            glfwSetWindowPos(handle, (vidMode.width() - width) / 2, (vidMode.height() - height) / 2);
        }

        // Input Callbacks
        keyboard = new Keyboard();
        mouse = new Mouse();
        glfwSetKeyCallback(handle, keyboard);
        glfwSetCursorPosCallback(handle, mouse.getMouseMoveCallback());
        glfwSetMouseButtonCallback(handle, mouse.getMouseButtonsCallback());
        glfwSetScrollCallback(handle, mouse.getMouseScrollCallback());

        // Resize Callback
        windowSizeCallback = new GLFWWindowSizeCallback() {
            @Override
            public void invoke(long window, int newWidth, int newHeight) {
                width = newWidth;
                height = newHeight;
                wasResized = true;
                if (contentPane != null) contentPane.setSize(newWidth, newHeight);
            }
        };
        glfwSetWindowSizeCallback(handle, windowSizeCallback);

        lastFrameTime = glfwGetTime();
        lastFPSTime = System.currentTimeMillis();
    }

    public static void update() {
        Mouse.endFrame();
        glfwPollEvents();

        double currentFrameTime = glfwGetTime();
        deltaInSeconds = currentFrameTime - lastFrameTime;
        lastFrameTime = currentFrameTime;

        if (showFPSTitle) {
            frames++;
            if (System.currentTimeMillis() - lastFPSTime >= 1000) {
                glfwSetWindowTitle(handle, TITLE + " | FPS: " + frames);
                lastFPSTime = System.currentTimeMillis();
                frames = 0;
            }
        }
    }

    public static void destroy() {
        mouse.destroy();
        keyboard.close();
        if (windowSizeCallback != null) windowSizeCallback.free();
        glfwDestroyWindow(handle);
        glfwTerminate();
    }

    // --- GETTERS & SETTERS ---
    public static long getHandle()
    {
        return handle;
    }
    public static boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }
    public static int getWidth() {
        return width;
    }
    public static int getHeight() {
        return height;
    }
    public static float getDeltaInSeconds() {
        return (float) deltaInSeconds;
    }
    public static void setShowFPSTitle(boolean show) {
        showFPSTitle = show;
    }
    public static ui.Container getContentPane() {
        return contentPane;
    }
    public static void setContentPane(ui.Container pane) {
        contentPane = pane;
        if (contentPane != null) contentPane.setSize(width, height);
    }
}