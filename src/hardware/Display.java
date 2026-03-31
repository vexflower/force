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
// === NEW: Required for Vulkan 1.2 Bindless Features ===
import static org.lwjgl.vulkan.VK12.*;
// Add these right below your other imports in Display.java
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import util.IntList;

import static org.lwjgl.vulkan.EXTDebugUtils.*;

public final class Display {

    private static int width = 1280;
    private static int height = 720;
    private static long window;
    private static final String TITLE = "Vulkan Engine";

    private static int frames;
    private static long lastFPSTime;
    private static boolean showFPSTitle = true;
    private static double lastFrameTime;
    private static double deltaInSeconds;

    private static VkInstance vkInstance;
    private static long surface;
    private static VkPhysicalDevice physicalDevice;
    private static VkDevice vkDevice;

    private static VkQueue graphicsQueue;
    private static VkQueue presentQueue;
    private static int graphicsQueueFamilyIndex = -1;
    private static int presentQueueFamilyIndex = -1;

    private static Keyboard keyboard;
    private static Mouse mouse;
    private static GLFWWindowSizeCallback windowSizeCallback;
    // [NEW: Validation Layer Control]
    public static final boolean ENABLE_VALIDATION_LAYERS = true;
    private static long debugMessenger;
    IntList displays = new IntList();

    // --- [NEW] UI ROOT ---
    private static ui.Container contentPane;

    private Display() {}

    public static void createDisplay(int index, int w, int h) {
        // this should allow multiple displays because why not. (for 2 monitor based games, or more)
        // index should be the index of the monitor, when created, it should be zero, then 1, then so on.

        if (!glfwInit()) throw new RuntimeException("ERROR: GLFW wasn't initialized");
        width = (w == 0) ? 1280 : w;
        height = (h == 0) ? 720 : h;

        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, TITLE, NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        GLFWVidMode vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidMode != null) {
            glfwSetWindowPos(window, (vidMode.width() - width) / 2, (vidMode.height() - height) / 2);
        }

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
                // Automatically stretch the Content Pane
                if (contentPane != null) {
                    contentPane.setSize(newWidth, newHeight);
                }
            }
        };
        glfwSetWindowSizeCallback(window, windowSizeCallback);

        initVulkan();

        lastFrameTime = glfwGetTime();
        lastFPSTime = System.currentTimeMillis();
    }

    private static void initVulkan() {
        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw new RuntimeException("ERROR: Vulkan is not supported on this device!");
        }
        createInstance();
        setupDebugMessenger();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
    }

    // [NEW: The Debug Callback]
    private static void setupDebugMessenger() {
        if (!ENABLE_VALIDATION_LAYERS) return;

        try (MemoryStack stack = stackPush()) {
            VkDebugUtilsMessengerCreateInfoEXT createInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT);

            // We want to see Warnings and Errors (ignoring verbose info spam)
            createInfo.messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
            createInfo.messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT);

            // The actual Java Lambda that gets called when Vulkan detects an error
            createInfo.pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                System.err.println("\n[VULKAN VALIDATION LAYER] " + callbackData.pMessageString());
                return VK_FALSE;
            });

            LongBuffer pDebugMessenger = stack.longs(VK_NULL_HANDLE);
            if (vkCreateDebugUtilsMessengerEXT(vkInstance, createInfo, null, pDebugMessenger) != VK_SUCCESS) {
                throw new RuntimeException("Failed to set up debug messenger!");
            }
            debugMessenger = pDebugMessenger.get(0);
        }
    }

    private static void createInstance() {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack);
            appInfo.sType(VK_STRUCTURE_TYPE_APPLICATION_INFO);
            appInfo.pApplicationName(stack.UTF8(TITLE));
            appInfo.applicationVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.pEngineName(stack.UTF8("Force Engine"));
            appInfo.engineVersion(VK_MAKE_VERSION(1, 0, 0));
            appInfo.apiVersion(VK_API_VERSION_1_2);

            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) throw new RuntimeException("Failed to find GLFW Vulkan extensions!");

            // [CHANGED: Add the Debug Extension if Validation is enabled]
            PointerBuffer requiredExtensions;
            if (ENABLE_VALIDATION_LAYERS) {
                requiredExtensions = stack.mallocPointer(glfwExtensions.capacity() + 1);
                requiredExtensions.put(glfwExtensions);
                requiredExtensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
                requiredExtensions.flip();
            } else {
                requiredExtensions = glfwExtensions;
            }

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            createInfo.pApplicationInfo(appInfo);
            createInfo.ppEnabledExtensionNames(requiredExtensions);

            // [CHANGED: Inject the Khronos Validation Layer]
            if (ENABLE_VALIDATION_LAYERS) {
                createInfo.ppEnabledLayerNames(stack.pointers(stack.UTF8("VK_LAYER_KHRONOS_validation")));
            }

            PointerBuffer instancePtr = stack.mallocPointer(1);
            if (vkCreateInstance(createInfo, null, instancePtr) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan instance!");
            }
            vkInstance = new VkInstance(instancePtr.get(0), createInfo);
            System.out.println("Vulkan Instance successfully created (v1.2)!");
        }
    }

    private static void createSurface() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            if (GLFWVulkan.glfwCreateWindowSurface(vkInstance, window, null, pSurface) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create window surface!");
            }
            surface = pSurface.get(0);
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
                queueInfo.pQueuePriorities(stack.floats(1.0f));
            }

            // 1. Standard Vulkan 1.0 Features
            VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack);

            // 2. Vulkan 1.2 Bindless Features
            VkPhysicalDeviceVulkan12Features bindlessFeatures = VkPhysicalDeviceVulkan12Features.calloc(stack);
            bindlessFeatures.sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES);
            bindlessFeatures.descriptorBindingPartiallyBound(true);
            bindlessFeatures.runtimeDescriptorArray(true);
            bindlessFeatures.shaderSampledImageArrayNonUniformIndexing(true);
            bindlessFeatures.descriptorBindingSampledImageUpdateAfterBind(true);

            // 3. NEW: The Modern Feature Wrapper (Vulkan 1.1+)
            // This safely bundles the 1.0 features and the 1.2 features together
            VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc(stack);
            features2.sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2);
            features2.features(deviceFeatures);
            features2.pNext(bindlessFeatures.address());

            // 4. Device Creation
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);
            createInfo.pQueueCreateInfos(queueCreateInfos);

            // CRITICAL: We attach our master wrapper to pNext.
            // We intentionally do NOT call createInfo.pEnabledFeatures() so it remains NULL!
            createInfo.pNext(features2.address());

            PointerBuffer extensions = stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
            createInfo.ppEnabledExtensionNames(extensions);

            PointerBuffer pDevice = stack.mallocPointer(1);
            if (vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device!");
            }
            vkDevice = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(vkDevice, graphicsQueueFamilyIndex, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), vkDevice);

            vkGetDeviceQueue(vkDevice, presentQueueFamilyIndex, 0, pQueue);
            presentQueue = new VkQueue(pQueue.get(0), vkDevice);

            System.out.println("Logical Device & Hardware Queues established with Bindless Support.");
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

    public static void closeDisplays()
    {
        // should close ALL possible displays here
    }

    public static void closeDisplay() {
        mouse.destroy();
        keyboard.close();
        if (windowSizeCallback != null) windowSizeCallback.free();

        if (vkDevice != null) {
            vkDestroyDevice(vkDevice, null);
        }

        if (ENABLE_VALIDATION_LAYERS && debugMessenger != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(vkInstance, debugMessenger, null);
        }

        if (vkInstance != null) {
            vkDestroySurfaceKHR(vkInstance, surface, null);
            vkDestroyInstance(vkInstance, null);
        }

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void setContentPane(ui.Container pane) {
        contentPane = pane;
        // Instantly force it to match the current window size
        if (contentPane != null) {
            contentPane.setSize(width, height);
        }
    }

    public static ui.Container getContentPane() {
        return contentPane;
    }

    public static boolean shouldDisplayClose() { return glfwWindowShouldClose(window); }
    public static float getDeltaInSeconds() { return (float) deltaInSeconds; }
    public static int getWidth() { return width; }
    public static int getHeight() { return height; }
    public static void setShowFPSTitle(boolean show) { showFPSTitle = show; }
    public static VkDevice getDevice() { return vkDevice; }
    public static VkPhysicalDevice getPhysicalDevice() { return physicalDevice; }
    public static long getSurface() { return surface; }
    public static long getWindow() { return window; }
    public static VkQueue getPresentQueue() { return presentQueue; }
    public static int getGraphicsQueueFamilyIndex() { return graphicsQueueFamilyIndex; }
    public static VkQueue getGraphicsQueue() { return graphicsQueue; }
}