package hardware;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK12.*;

public class VulkanContext {

    public static final boolean ENABLE_VALIDATION_LAYERS = true;

    private static VkInstance instance;
    private static long debugMessenger;
    private static long surface;
    private static VkPhysicalDevice physicalDevice;
    private static VkDevice device;

    private static VkQueue graphicsQueue;
    private static VkQueue presentQueue;
    private static int graphicsQueueFamilyIndex = -1;
    private static int presentQueueFamilyIndex = -1;

    public static void init() {
        if (!GLFWVulkan.glfwVulkanSupported()) {
            throw new RuntimeException("ERROR: Vulkan is not supported on this hardware!");
        }
        createInstance();
        setupDebugMessenger();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
    }

    private static void createInstance() {
        try (MemoryStack stack = stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                    .pApplicationName(stack.UTF8("Force Engine"))
                    .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                    .pEngineName(stack.UTF8("Force Engine"))
                    .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                    .apiVersion(VK_API_VERSION_1_2); // Require 1.2 for Bindless!

            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) throw new RuntimeException("Failed to find GLFW Vulkan extensions!");

            PointerBuffer requiredExtensions;
            if (ENABLE_VALIDATION_LAYERS) {
                requiredExtensions = stack.mallocPointer(glfwExtensions.capacity() + 1);
                requiredExtensions.put(glfwExtensions);
                requiredExtensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
                requiredExtensions.flip();
            } else {
                requiredExtensions = glfwExtensions;
            }

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(requiredExtensions);

            if (ENABLE_VALIDATION_LAYERS) {
                createInfo.ppEnabledLayerNames(stack.pointers(stack.UTF8("VK_LAYER_KHRONOS_validation")));
            }

            PointerBuffer instancePtr = stack.mallocPointer(1);
            if (vkCreateInstance(createInfo, null, instancePtr) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create Vulkan instance!");
            }
            instance = new VkInstance(instancePtr.get(0), createInfo);
            System.out.println("Vulkan Context Booted Successfully (v1.2).");
        }
    }

    private static void setupDebugMessenger() {
        if (!ENABLE_VALIDATION_LAYERS) return;
        try (MemoryStack stack = stackPush()) {
            VkDebugUtilsMessengerCreateInfoEXT createInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                    .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                    .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                    .pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                        VkDebugUtilsMessengerCallbackDataEXT data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                        System.err.println("\n[VULKAN VALIDATION] " + data.pMessageString());
                        return VK_FALSE;
                    });

            LongBuffer pDebugMessenger = stack.longs(VK_NULL_HANDLE);
            if (vkCreateDebugUtilsMessengerEXT(instance, createInfo, null, pDebugMessenger) != VK_SUCCESS) {
                throw new RuntimeException("Failed to set up debug messenger!");
            }
            debugMessenger = pDebugMessenger.get(0);
        }
    }

    private static void createSurface() {
        try (MemoryStack stack = stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            if (GLFWVulkan.glfwCreateWindowSurface(instance, Window.getHandle(), null, pSurface) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create window surface!");
            }
            surface = pSurface.get(0);
        }
    }

    private static void pickPhysicalDevice() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer deviceCount = stack.mallocInt(1);
            vkEnumeratePhysicalDevices(instance, deviceCount, null);
            if (deviceCount.get(0) == 0) throw new RuntimeException("Failed to find GPUs with Vulkan support!");

            PointerBuffer devices = stack.mallocPointer(deviceCount.get(0));
            vkEnumeratePhysicalDevices(instance, deviceCount, devices);

            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice dev = new VkPhysicalDevice(devices.get(i), instance);
                if (isDeviceSuitable(dev, stack)) {
                    physicalDevice = dev;
                    break;
                }
            }

            if (physicalDevice == null) throw new RuntimeException("Failed to find a suitable GPU!");

            VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(physicalDevice, props);
            System.out.println("Hardware Selected: " + props.deviceNameString());
        }
    }

    private static boolean isDeviceSuitable(VkPhysicalDevice dev, MemoryStack stack) {
        IntBuffer queueFamilyCount = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(dev, queueFamilyCount, null);

        VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(dev, queueFamilyCount, queueFamilies);

        for (int i = 0; i < queueFamilies.capacity(); i++) {
            if ((queueFamilies.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) graphicsQueueFamilyIndex = i;

            IntBuffer presentSupport = stack.mallocInt(1);
            vkGetPhysicalDeviceSurfaceSupportKHR(dev, i, surface, presentSupport);
            if (presentSupport.get(0) == VK_TRUE) presentQueueFamilyIndex = i;

            if (graphicsQueueFamilyIndex >= 0 && presentQueueFamilyIndex >= 0) return true;
        }
        return false;
    }

    private static void createLogicalDevice() {
        try (MemoryStack stack = stackPush()) {
            int[] uniqueQueueFamilies = (graphicsQueueFamilyIndex == presentQueueFamilyIndex)
                    ? new int[] { graphicsQueueFamilyIndex }
                    : new int[] { graphicsQueueFamilyIndex, presentQueueFamilyIndex };

            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(uniqueQueueFamilies.length, stack);
            for (int i = 0; i < uniqueQueueFamilies.length; i++) {
                queueCreateInfos.get(i).sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                        .queueFamilyIndex(uniqueQueueFamilies[i]).pQueuePriorities(stack.floats(1.0f));
            }

            VkPhysicalDeviceFeatures deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack).multiDrawIndirect(true);

            // Bindless Features required by the Ubershader
            VkPhysicalDeviceVulkan12Features bindlessFeatures = VkPhysicalDeviceVulkan12Features.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
                    .descriptorBindingPartiallyBound(true)
                    .runtimeDescriptorArray(true)
                    .shaderSampledImageArrayNonUniformIndexing(true)
                    .descriptorBindingSampledImageUpdateAfterBind(true);

            VkPhysicalDeviceFeatures2 features2 = VkPhysicalDeviceFeatures2.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2)
                    .features(deviceFeatures)
                    .pNext(bindlessFeatures.address());

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                    .pQueueCreateInfos(queueCreateInfos)
                    .pNext(features2.address())
                    .ppEnabledExtensionNames(stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME)));

            PointerBuffer pDevice = stack.mallocPointer(1);
            if (vkCreateDevice(physicalDevice, createInfo, null, pDevice) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device!");
            }
            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, graphicsQueueFamilyIndex, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), device);

            vkGetDeviceQueue(device, presentQueueFamilyIndex, 0, pQueue);
            presentQueue = new VkQueue(pQueue.get(0), device);
        }
    }

    public static void destroy() {
        if (device != null) vkDestroyDevice(device, null);
        if (ENABLE_VALIDATION_LAYERS && debugMessenger != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
        }
        if (instance != null) {
            vkDestroySurfaceKHR(instance, surface, null);
            vkDestroyInstance(instance, null);
        }
    }

    // --- GETTERS ---
    public static VkDevice getDevice() {
        return device;
    }
    public static VkPhysicalDevice getPhysicalDevice() {
        return physicalDevice;
    }
    public static long getSurface() {
        return surface;
    }
    public static VkQueue getGraphicsQueue() {
        return graphicsQueue;
    }
    public static VkQueue getPresentQueue() {
        return presentQueue;
    }
    public static int getGraphicsQueueFamilyIndex() {
        return graphicsQueueFamilyIndex;
    }
}