package mchorse.bbs_mod.graphics.window;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Native LWJGL 2 boundary for BBS input. Saved key IDs remain version independent. */
public class Window
{
    private static int verticalScroll;
    private static long lastScroll;
    private static final Set<Object> cursorHolders = new HashSet<>();
    private static final Map<Integer, Cursor> cursors = new HashMap<>();
    private static int currentCursorShape = -1;
    private static boolean cursorClipCaptured;
    private static boolean previousCursorClip;

    public static void setVerticalScroll(int scroll)
    {
        verticalScroll = scroll;
        lastScroll = System.currentTimeMillis();
    }

    public static int getVerticalScroll()
    {
        return lastScroll + 5 < System.currentTimeMillis() ? 0 : verticalScroll;
    }

    public static boolean isMouseButtonPressed(int mouse)
    {
        return Mouse.isCreated() && mouse >= 0 && Mouse.isButtonDown(mouse);
    }

    public static boolean isCtrlPressed() { return GuiScreen.isCtrlKeyDown(); }
    public static boolean isShiftPressed() { return GuiScreen.isShiftKeyDown(); }
    public static boolean isAltPressed() { return GuiScreen.isAltKeyDown(); }

    public static boolean isKeyPressed(int key)
    {
        int nativeKey = InputCodes.toNative(key);
        return Keyboard.isCreated() && nativeKey > Keyboard.KEY_NONE && Keyboard.isKeyDown(nativeKey);
    }

    public static String getClipboard() { return GuiScreen.getClipboardString(); }
    public static MapType getClipboardMap() { return DataToString.mapFromString(getClipboard()); }
    public static ListType getClipboardList() { return DataToString.listFromString(getClipboard()); }

    public static MapType getClipboardMap(String verificationKey)
    {
        MapType data = getClipboardMap();
        return data != null && data.getBool(verificationKey) ? data : null;
    }

    public static void setClipboard(String string) { GuiScreen.setClipboardString(string); }

    public static void setClipboard(BaseType data)
    {
        if (data != null) setClipboard(DataToString.toString(data, true));
    }

    public static void setClipboard(MapType data, String verificationKey)
    {
        if (data != null) data.putBool(verificationKey, true);
        setClipboard(data);
    }

    public static void moveCursor(int x, int y)
    {
        Mouse.setCursorPosition(x, Minecraft.getMinecraft().displayHeight - y - 1);
    }

    public static void setCursorHidden(Object holder, boolean hidden)
    {
        if (hidden) cursorHolders.add(holder);
        else cursorHolders.remove(holder);
        boolean hide = !cursorHolders.isEmpty();
        if (!Mouse.isCreated()) return;

        /* LWJGL 2 clips its virtual x/y to the window even while grabbed. BBS
         * free look consumes absolute coordinates, just as GLFW does, so those
         * coordinates must remain unbounded until the last holder releases. */
        if (hide)
        {
            if (!cursorClipCaptured)
            {
                previousCursorClip = Mouse.isClipMouseCoordinatesToWindow();
                cursorClipCaptured = true;
            }
            Mouse.setClipMouseCoordinatesToWindow(false);
        }
        else if (cursorClipCaptured)
        {
            Mouse.setClipMouseCoordinatesToWindow(previousCursorClip);
            cursorClipCaptured = false;
        }
        if (!hide && Minecraft.getMinecraft().currentScreen == null) return;
        if (Mouse.isGrabbed() != hide) Mouse.setGrabbed(hide);
    }

    public static void setStandardCursor(int shape)
    {
        if (!Mouse.isCreated() || Mouse.isGrabbed())
        {
            currentCursorShape = -1;
            return;
        }
        if (shape == currentCursorShape) return;
        try
        {
            Cursor cursor = null;
            if (shape != InputCodes.ARROW_CURSOR)
            {
                cursor = cursors.get(shape);
                if (cursor == null)
                {
                    cursor = createCursor(shape);
                    cursors.put(shape, cursor);
                }
            }
            Mouse.setNativeCursor(cursor);
            currentCursorShape = shape;
        }
        catch (org.lwjgl.LWJGLException e)
        {
            System.err.println("BBS: cannot set cursor: " + e.getMessage());
        }
    }

    private static Cursor createCursor(int shape) throws org.lwjgl.LWJGLException
    {
        int size = Math.max(16, Cursor.getMinCursorSize());
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        int center = 7;
        for (int pass = 0; pass < 2; pass++)
        {
            graphics.setColor(pass == 0 ? Color.WHITE : Color.BLACK);
            graphics.setStroke(new BasicStroke(pass == 0 ? 3 : 1));
            if (shape == InputCodes.IBEAM_CURSOR)
            {
                graphics.drawLine(center, 1, center, 14);
                graphics.drawLine(4, 1, 10, 1);
                graphics.drawLine(4, 14, 10, 14);
            }
            else if (shape == InputCodes.HAND_CURSOR)
            {
                Polygon hand = new Polygon(new int[] {4, 4, 6, 6, 8, 10, 12, 13, 13, 10, 6, 2, 2},
                    new int[] {9, 1, 1, 6, 5, 6, 6, 8, 12, 15, 15, 10, 8}, 13);
                graphics.drawPolygon(hand);
            }
            else
            {
                if (shape != InputCodes.VRESIZE_CURSOR)
                {
                    graphics.drawLine(1, center, 14, center);
                    if (shape == InputCodes.HRESIZE_CURSOR)
                    {
                        graphics.drawLine(1, center, 4, 4); graphics.drawLine(1, center, 4, 10);
                        graphics.drawLine(14, center, 11, 4); graphics.drawLine(14, center, 11, 10);
                    }
                }
                if (shape != InputCodes.HRESIZE_CURSOR)
                {
                    graphics.drawLine(center, 1, center, 14);
                    if (shape == InputCodes.VRESIZE_CURSOR)
                    {
                        graphics.drawLine(center, 1, 4, 4); graphics.drawLine(center, 1, 10, 4);
                        graphics.drawLine(center, 14, 4, 11); graphics.drawLine(center, 14, 10, 11);
                    }
                }
            }
        }
        graphics.dispose();
        IntBuffer pixels = BufferUtils.createIntBuffer(size * size);
        for (int y = size - 1; y >= 0; y--)
            for (int x = 0; x < size; x++) pixels.put(image.getRGB(x, y));
        pixels.flip();
        int hotX = shape == InputCodes.HAND_CURSOR ? 5 : center;
        int hotY = shape == InputCodes.HAND_CURSOR ? 1 : center;
        return new Cursor(size, size, hotX, size - hotY - 1, 1, pixels, null);
    }

    public static void resetCursor() { setStandardCursor(InputCodes.ARROW_CURSOR); }
}
