package engine.graphics.glfw;

import engine.graphics.vulkan.VulkanInstance;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkInstance;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class GLFWVulkanWindow extends GLFWWindow {

    private VkInstance vulkanInstance;

    public GLFWVulkanWindow(){
        super();
    }

    public GLFWVulkanWindow(int width, int height, String title) {
        super(width, height, title);
    }

//    @Override
//    public void doRender(Runnable renderAction) {
//        glfwPollEvents();
//        var vulkanManager = ((EngineRenderContext) Platform.getEngineClient().getRenderContext()).getVulkanManager();
//        if(resized) {
//            vulkanManager.getSwapchainHolder().markRecreation();
//        }
//        vulkanManager.getSwapchainHolder().tick();
//        var pWaitDstStageMask = memAllocInt(1);
//        var pImageIndex = memAllocInt(1);
//        var pSwapchains = memAllocLong(1);
//        var pCommandBuffers = memAllocPointer(1);
//        pWaitDstStageMask.put(0, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
//        var imageAcquiredSemaphore = Semaphore.createPendingSemaphore(vulkanManager.getDeviceQueueFamily().getLogicalDevice());
//        var renderCompleteSemaphore = Semaphore.createPendingSemaphore(vulkanManager.getDeviceQueueFamily().getLogicalDevice());
//        VkSubmitInfo submitInfo = VkSubmitInfo.calloc()
//                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
//                .pNext(NULL)
//                .waitSemaphoreCount(imageAcquiredSemaphore.getSemaphorePointer().remaining())
//                .pWaitSemaphores(imageAcquiredSemaphore.getSemaphorePointer())
//                .pWaitDstStageMask(pWaitDstStageMask)
//                .pCommandBuffers(pCommandBuffers)
//                .pSignalSemaphores(renderCompleteSemaphore.getSemaphorePointer());
//
//        // Info struct to present the current swapchain image to the display
//        VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc()
//                .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
//                .pNext(NULL)
//                .pWaitSemaphores(renderCompleteSemaphore.getSemaphorePointer())
//                .swapchainCount(pSwapchains.remaining())
//                .pSwapchains(pSwapchains)
//                .pImageIndices(pImageIndex)
//                .pResults(null);
//        imageAcquiredSemaphore.createSemaphore();
//        renderCompleteSemaphore.createSemaphore();
//        var err = vkAcquireNextImageKHR(vulkanManager.getDeviceQueueFamily().getLogicalDevice(), vulkanManager.getSwapchainHolder().getSwapchain().getSwapchainHandle(), -1L, imageAcquiredSemaphore.getSemaphorePointer().get(0), VK_NULL_HANDLE, pImageIndex);
//        int currentBuffer = pImageIndex.get(0);
//        if (err != VK_SUCCESS) {
//            throw new AssertionError("Failed to acquire next swapchain image: " + GLHelper.translateVulkanResult(err));
//        }
//        pCommandBuffers.put(0, vulkanManager.getRenderCommandBuffers()[currentBuffer].getVulkanBuffer());
//        err = vkQueueSubmit(vulkanManager.getQueue(), submitInfo, VK_NULL_HANDLE);
//        if (err != VK_SUCCESS) {
//            throw new AssertionError("Failed to submit render queue: " + GLHelper.translateVulkanResult(err));
//        }
//
//        // Present the current buffer to the swap chain
//        // This will display the image
//        pSwapchains.put(0, vulkanManager.getSwapchainHolder().getSwapchain().getSwapchainHandle());
//        err = vkQueuePresentKHR(vulkanManager.getQueue(), presentInfo);
//        if (err != VK_SUCCESS) {
//            throw new AssertionError("Failed to present the swapchain image: " + GLHelper.translateVulkanResult(err));
//        }
//        // Create and submit post present barrier
//        vkQueueWaitIdle(vulkanManager.getQueue());
////        renderAction.run();
//        imageAcquiredSemaphore.dispose();
//        renderCompleteSemaphore.dispose();
//        presentInfo.free();
//        memFree(pWaitDstStageMask);
//        submitInfo.free();
//        memFree(pSwapchains);
//        memFree(pCommandBuffers);
//    }
//
//    @Override
//    public void beginRender() {
//        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
//    }
//
//    @Override
//    public void endRender() {
//        glfwSwapBuffers(pointer);
//
//        if (isResized()) {
//            resized = false;
//        }
//
//        glfwPollEvents();
//    }

    @Override
    public void init() {
        setScreen(GLFWContext.getPrimaryScreen());
        initWindowHint();
        pointer = glfwCreateWindow(width, height, title, NULL, NULL);
        checkCreated();
        disposable = createDisposable(pointer);
        width *= getContentScaleX();
        height *= getContentScaleY(); // pre-scale it to prevent weird behavior of Gui caused by missed call of resize()
        initCallbacks();
        setWindowPosCenter();
        cursor = new GLFWCursor(pointer);
        resize();

//        if(!GLFWVulkan.glfwVulkanSupported())
//            throw new IllegalStateException("Vulkan not supported in GLFW");
//        var vulkanManager = ((EngineRenderContext) Platform.getEngineClient().getRenderContext()).getVulkanManager();
//        vulkanManager.createVulkanInstance(title);
//        vulkanManager.setupDebugging(VK_DEBUG_REPORT_ERROR_BIT_EXT | VK_DEBUG_REPORT_WARNING_BIT_EXT, new VkDebugReportCallbackEXT() {
//            @Override
//            public int invoke(int flags, int objectType, long object, long location, int messageCode, long pLayerPrefix, long pMessage, long pUserData) {
//                var msg = VkDebugReportCallbackEXT.getString(pMessage);
//                switch (flags){
//                    case VK_DEBUG_REPORT_DEBUG_BIT_EXT:
//                        Platform.getLogger().debug(msg);
//                        break;
//                    case VK_DEBUG_REPORT_INFORMATION_BIT_EXT:
//                        Platform.getLogger().info(msg);
//                        break;
//                    case VK_DEBUG_REPORT_WARNING_BIT_EXT:
//                        Platform.getLogger().warn(msg);
//                        break;
//                    case VK_DEBUG_REPORT_ERROR_BIT_EXT:
//                        Platform.getLogger().error(msg);
//                        break;
//                }
//                return 0;
//            }
//        });
//        vulkanManager.preInitVulkanResources();

//        vulkanManager.createWindowSurface(pointer);
//        vulkanManager.initVulkanResources();
    }

    @Override
    protected void initWindowHint() {
        super.initWindowHint();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
    }

    public PointerBuffer getRequiredExtensions(){
        return GLFWVulkan.glfwGetRequiredInstanceExtensions();
    }

    public long getSurface(VulkanInstance instance){
        try(var stack = MemoryStack.stackPush()){
            var buf = stack.mallocLong(1);
            GLFWVulkan.glfwCreateWindowSurface(instance.getNativeInstance(), getPointer(), null, buf);
            return buf.get(0);
        }
    }

}