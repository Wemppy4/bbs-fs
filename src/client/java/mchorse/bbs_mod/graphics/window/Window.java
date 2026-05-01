package mchorse.bbs_mod.graphics.window;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Window
{
    private static int verticalScroll;
    private static long lastScroll;
    private static final Map<Integer, Long> standardCursors = new HashMap<>();
    private static int currentCursorShape = -1;

    public static long getWindow()
    {
        return MinecraftClient.getInstance().getWindow().getHandle();
    }

    public static void setVerticalScroll(int scroll)
    {
        verticalScroll = scroll;
        lastScroll = System.currentTimeMillis();
    }

    public static int getVerticalScroll()
    {
        if (lastScroll + 5 < System.currentTimeMillis())
        {
            return 0;
        }

        return verticalScroll;
    }

    public static boolean isMouseButtonPressed(int mouse)
    {
        return GLFW.glfwGetMouseButton(getWindow(), mouse) == GLFW.GLFW_PRESS;
    }

    public static boolean isCtrlPressed()
    {
        return Screen.hasControlDown();
    }

    public static boolean isShiftPressed()
    {
        return Screen.hasShiftDown();
    }

    public static boolean isAltPressed()
    {
        return Screen.hasAltDown();
    }

    public static boolean isKeyPressed(int key)
    {
        return InputUtil.isKeyPressed(getWindow(), key);
    }

    public static String getClipboard()
    {
        try
        {
            String string = GLFW.glfwGetClipboardString(getWindow());

            return string == null ? "" : string;
        }
        catch (Exception e)
        {}

        return "";
    }

    public static MapType getClipboardMap()
    {
        return DataToString.mapFromString(getClipboard());
    }

    /**
     * Get a data map from clipboard with verification key.
     */
    public static MapType getClipboardMap(String verificationKey)
    {
        MapType data = DataToString.mapFromString(getClipboard());

        return data != null && data.getBool(verificationKey) ? data : null;
    }

    public static ListType getClipboardList()
    {
        return DataToString.listFromString(getClipboard());
    }

    public static void setClipboard(String string)
    {
        if (string.length() > 1024)
        {
            byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length + 1);

            buffer.put(bytes);
            buffer.put((byte) 0);
            buffer.flip();

            GLFW.glfwSetClipboardString(getWindow(), buffer);

            MemoryUtil.memFree(buffer);
        }
        else
        {
            GLFW.glfwSetClipboardString(getWindow(), string);
        }
    }

    public static void setClipboard(BaseType data)
    {
        if (data != null)
        {
            setClipboard(DataToString.toString(data, true));
        }
    }

    /**
     * Save given data to clipboard with a verification key that could be
     * used in {@link #getClipboardMap(String)} to decode data.
     */
    public static void setClipboard(MapType data, String verificationKey)
    {
        if (data != null)
        {
            data.putBool(verificationKey, true);
        }

        setClipboard(data);
    }

    public static void moveCursor(int x, int y)
    {
        GLFW.glfwSetCursorPos(getWindow(), x, y);
    }

    public static void setStandardCursor(int shape)
    {
        long window = getWindow();

        if (GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED)
        {
            currentCursorShape = -1;

            return;
        }

        if (currentCursorShape == shape)
        {
            return;
        }

        long cursor = standardCursors.computeIfAbsent(shape, GLFW::glfwCreateStandardCursor);

        GLFW.glfwSetCursor(window, cursor);
        currentCursorShape = shape;
    }

    public static void resetCursor()
    {
        setStandardCursor(GLFW.GLFW_ARROW_CURSOR);
    }
}
