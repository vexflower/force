package hardware;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;

/**
 * AAA Vulkan Hardware and Window Context.
 * Pure Vulkan Implementation - Zero OpenGL.
 */
public final class Display {

    private static int width = 1280;
    private static int height = 720;
    private static long window;
    private static final String TITLE = "Vulkan Engine";

    // Timing and FPS
    private static int frames;
    private static long lastFPSTime;
    private static boolean showFPSTitle = true;
    private static double lastFrameTime;
    private static double deltaInSeconds;

    // Vulkan Core State
    private static VkInstance vkInstance;
    private static long surface;
    private static VkPhysicalDevice physicalDevice;
    private static VkDevice vkDevice; // The Logical Device

    // Vulkan Hardware Queues
    private static VkQueue graphicsQueue;
    private static VkQueue presentQueue;
    private static int graphicsQueueFamilyIndex = -1;
    private static int presentQueueFamilyIndex = -1;

    // Input Hardware
    private static Keyboard keyboard;
    private static Mouse mouse;
    private static GLFWWindowSizeCallback windowSizeCallback;

    private Display() {}

    public static void createDisplay(int w, int h) {
        if (!glfwInit()) throw new RuntimeException("ERROR: GLFW wasn't initialized");
        width = (w == 0) ? 1280 : w;
        height = (h == 0) ? 720 : h;

        // 1. Tell GLFW to completely ignore OpenGL
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, TITLE, NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            glfwSetWindowPos(window, (vidMode.width() - width) / 2, (vidMode.height() - height) / 2);
        }

        // Initialize Hardware Listeners
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
                // Note: In Vulkan, resizing requires completely destroying and rebuilding the Swapchain!
            }
        };
        glfwSetWindowSizeCallback(window, windowSizeCallback);

        // 2. Boot the Vulkan Pipeline
        initVulkan();

        lastFrameTime = glfwGetTime();
        lastFPSTime = System.currentTimeMillis();
    }

    private static void initVulkan() {
        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw new RuntimeException("ERROR: Vulkan is not supported on this device!");
        }
        createInstance();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
    }

    private static void createInstance() {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack);
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
            appInfo.pApplicationName(stack.UTF8(TITLE));
            appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.pEngineName(stack.UTF8("Force Engine"));
            appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.apiVersion(VK_API_VERSION_1_0);

            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) throw new RuntimeException("Failed to find GLFW Vulkan extensions!");

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            createInfo.pApplicationInfo(appInfo);
            createInfo.ppEnabledExtensionNames(glfwExtensions);

            PointerBuffer instancePtr = stack.mallocPointer(1);
            if (vkCreateInstance(createInfo, null, instancePtr) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan instance!");
            }
            vkInstance = new VkInstance(instancePtr.get(0), createInfo);
            System.out.println("Vulkan Instance successfully created!");
        }
    }

    private static void createSurface() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            if (GLFWVulkan.glfwCreateWindowSurface(vkInstance, window, null, pSurface) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create window surface!");
            }
            surface = pSurface.get(0);
            System.out.println("Vulkan Window Surface attached!");
        }
    }

    private static void pickPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer deviceCount = stack.mallocInt(1);
            vkEnumeratePhysicalDevices(vkInstance, deviceCount, null);
            if (deviceCount.get(0) == 0) throw new RuntimeException("Failed to find GPUs with Vulkan support!");

            PointerBuffer devices = stack.mallocPointer(deviceCount.get(0));
            vkEnumeratePhysicalDevices(vkInstance, deviceCount, devices);

            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice device = new VkPhysicalDevice(devices.get(i), vkInstance);
                if (isDeviceSuitable(device, stack)) {
                    physicalDevice = device;
                    break;
                }
            }

            if (physicalDevice == null) throw new RuntimeException("Failed to find a suitable GPU!");

            VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(physicalDevice, props);
            System.out.println("Selected Vulkan Hardware: " + props.deviceNameString());
        }
    }

    private static boolean isDeviceSuitable(VkPhysicalDevice device, MemoryStack stack) {
        IntBuffer queueFamilyCount = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);

        VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

        for (int i = 0; i < queueFamilies.capacity(); i++) {
            if ((queueFamilies.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                graphicsQueueFamilyIndex = i;
            }

            IntBuffer presentSupport = stack.mallocInt(1);
            vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport);
            if (presentSupport.get(0) == VK_TRUE) {
                presentQueueFamilyIndex = i;
            }

            if (graphicsQueueFamilyIndex >= 0 && presentQueueFamilyIndex >= 0) return true;
        }
        return false;
    }

    private static void createLogicalDevice() {
        try (MemoryStack stack = stackPush()) {

            // Usually, the graphics and present queues are the exact same index.
            // If they are different, we must create two separate queues.
            int[] uniqueQueueFamilies;
            if (graphicsQueueFamilyIndex == presentQueueFamilyIndex) {
                uniqueQueueFamilies = new int[] { graphicsQueueFamilyIndex };
            } else {
                uniqueQueueFamilies = new int[] { graphicsQueueFamilyIndex, presentQueueFamilyIndex };
            }

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.length, stack);
            for (int i = 0; i < uniqueQueueFamilies.length; i++) {
                VkDeviceQueueCreateInfo queueInfo = queueCreateInfos.get(i);
                queueInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
                queueInfo.queueFamilyIndex(uniqueQueueFamilies[i]);
                queueInfo.pQueuePriorities(stack.floats(1.0f)); // Give maximum priority to our graphics queue
            }

            // Features we want to ask the GPU for (e.g., Anisotropic filtering, geometry shaders)
            // For now, we leave it blank/default
            VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack);

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            createInfo.pQueueCreateInfos(queueCreateInfos);
            createInfo.pEnabledFeatures(deviceFeatures);

            // CRITICAL: We must request the Swapchain extension so we can actually draw to the screen later!
            PointerBuffer extensions = stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
            createInfo.ppEnabledExtensionNames(extensions);

            // Create the Logical Device
            PointerBuffer pDevice = stack.mallocPointer(1);
            if (vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device!");
            }
            vkDevice = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            // Retrieve the hardware queue pointers
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(vkDevice, graphicsQueueFamilyIndex, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), vkDevice);

            vkGetDeviceQueue(vkDevice, presentQueueFamilyIndex, 0, pQueue);
            presentQueue = new VkQueue(pQueue.get(0), vkDevice);

            System.out.println("Logical Device & Hardware Queues established.");
        }
    }

    public static void updateDisplay() {
        Mouse.endFrame();
        glfwPollEvents();

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

        // Vulkan Memory Management is ruthless. Destroy in exact reverse order!
        if (vkDevice != null) {
            vkDestroyDevice(vkDevice, null);
        }
        if (vkInstance != null) {
            vkDestroySurfaceKHR(vkInstance, surface, null);
            vkDestroyInstance(vkInstance, null);
        }

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static boolean shouldDisplayClose() {
        return glfwWindowShouldClose(window);
    }

    public static float getDeltaInSeconds() { return (float) deltaInSeconds; }
    public static int getWidth() { return width; }
    public static int getHeight() { return height; }
    public static void setShowFPSTitle(boolean show) { showFPSTitle = show; }
    public static org.lwjgl.vulkan.VkDevice getDevice() { return vkDevice; }
    public static org.lwjgl.vulkan.VkPhysicalDevice getPhysicalDevice() { return physicalDevice; }
    public static long getSurface() { return surface; }
    public static long getWindow() { return window; }
}