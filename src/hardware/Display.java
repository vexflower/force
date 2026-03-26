package hardware;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWWindowSizeCallback;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.createCapabilities;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_MULTISAMPLE;

/**
 * High-performance window and context management.
 */
public final class Display {
    private static int width = 1280;
    private static int height = 720;
    private static long window;
    private static final String TITLE = "Game Engine";

    // Timing and FPS
    private static int frames;
    private static long lastFPSTime;
    private static boolean showFPSTitle;
    private static double lastFrameTime;
    private static double deltaInSeconds;

    private static Keyboard keyboard;
    private static Mouse mouse;
    private static GLFWWindowSizeCallback windowSizeCallback;

    private Display() {}

    public static void createDisplay(int w, int h) {
        if (!glfwInit()) {
            throw new RuntimeException("ERROR: GLFW wasn't initialized");
        }

        width = (w == 0) ? 1280 : w;
        height = (h == 0) ? 720 : h;

        glfwWindowHint(GLFW_DEPTH_BITS, 32);
        glfwWindowHint(GLFW_RESIZABLE, GL_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);
        glfwWindowHint(GLFW_DECORATED, GL_TRUE);
        glfwWindowHint(GLFW_VISIBLE, GL_FALSE);

        window = glfwCreateWindow(width, height, TITLE, 0, 0);
        if (window == 0) throw new RuntimeException("Failed to create window");

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            glfwSetWindowPos(window, (vidMode.width() - width) / 2, (vidMode.height() - height) / 2);
        }

        // Initialize Hardware
        keyboard = new Keyboard();
        mouse = new Mouse();

        glfwSetKeyCallback(window, keyboard);
        glfwSetCursorPosCallback(window, mouse.getMouseMoveCallback());
        glfwSetMouseButtonCallback(window, mouse.getMouseButtonsCallback());
        glfwSetScrollCallback(window, mouse.getMouseScrollCallback());

        windowSizeCallback = new GLFWWindowSizeCallback() {
            @Override
            public void invoke(long window, int newWidth, int newHeight) {
                Display.width = newWidth;
                Display.height = newHeight;
                glViewport(0, 0, newWidth, newHeight);


            }
        };
        glfwSetWindowSizeCallback(window, windowSizeCallback);

        glfwMakeContextCurrent(window);
        createCapabilities();
        glfwShowWindow(window);

        // VSync: 0 = Uncapped, 1 = 60Hz/Monitor Refresh
        glfwSwapInterval(0);

        lastFrameTime = glfwGetTime();
        lastFPSTime = System.currentTimeMillis();
        glEnable(GL_MULTISAMPLE);
    }

    /**
     * Polls hardware events and swaps buffers.
     * Note: Calling glfwPollEvents() is what triggers the Mouse/Keyboard listener callbacks!
     */
    public static void updateDisplay() {
        // Clear delta states that should only last one frame
        Mouse.endFrame();

        // This instantly triggers your KeyListeners and MouseListeners if the user acted
        glfwPollEvents();

        glfwSwapBuffers(window);

        double currentFrameTime = glfwGetTime();
        deltaInSeconds = currentFrameTime - lastFrameTime;
        lastFrameTime = currentFrameTime;

        if (showFPSTitle) {
            frames++;
            if (System.currentTimeMillis() - lastFPSTime >= 1000) {
                glfwSetWindowTitle(window, TITLE + " | FPS: " + frames);
                lastFPSTime = System.currentTimeMillis();
                frames = 0;
            }
        }
    }

    public static void closeDisplay() {
        mouse.destroy();
        keyboard.close();
        if (windowSizeCallback != null) windowSizeCallback.free();
        glfwWindowShouldClose(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static boolean shouldDisplayClose() {
        return glfwWindowShouldClose(window);
    }

    public static float getDeltaInSeconds() {
        return (float) deltaInSeconds;
    }

    public static int getWidth() { return width; }
    public static int getHeight() { return height; }
    public static void setShowFPSTitle(boolean show) { showFPSTitle = show; }
}