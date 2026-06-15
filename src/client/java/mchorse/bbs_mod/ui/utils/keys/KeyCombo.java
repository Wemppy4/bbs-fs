package mchorse.bbs_mod.ui.utils.keys;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KeyCombo
{
    private static Set<String> categoryKeys = new HashSet<>();

    public String id = "";
    public IKey label;
    public IKey category = IKey.EMPTY;
    public String categoryKey;
    public boolean repeatable;
    public List<Integer> keys = new ArrayList<>();

    public static Set<String> getCategoryKeys()
    {
        return categoryKeys;
    }

    public KeyCombo(String id, IKey label, int... keys)
    {
        this(label, keys);

        this.id = id;

        this.categoryKey("all");
    }

    public KeyCombo(IKey label, int... keys)
    {
        this.label = label;

        this.set(keys);
    }

    private void set(int... keys)
    {
        this.keys.clear();

        for (int key : keys)
        {
            this.keys.add(key);
        }
    }

    public KeyCombo repeatable()
    {
        this.repeatable = true;

        return this;
    }

    public KeyCombo category(IKey category)
    {
        this.category = category;

        return this;
    }

    public KeyCombo categoryKey(String categoryKey)
    {
        this.categoryKey = categoryKey;

        categoryKeys.add(categoryKey);

        return this;
    }

    public int getMainKey()
    {
        return this.keys.isEmpty() ? -1 : this.keys.get(0);
    }

    /**
     * Whether every key in this combo is currently held down — keyboard keys, modifiers and mouse
     * buttons alike (mouse buttons are stored as negative ids). Use this for hold-to-act behaviour,
     * since the keybind system only dispatches discrete presses, not holds.
     */
    public boolean isHeld()
    {
        if (this.keys.isEmpty())
        {
            return false;
        }

        for (int key : this.keys)
        {
            if (!isKeyDown(key))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Whether a single combo key is down right now. Negative ids are mouse buttons; the shift/ctrl/alt
     * modifiers map to the left/right-agnostic checks so either side counts. Shared by {@link #isHeld()}
     * and {@link Keybind} so the held-key logic lives in one place.
     */
    public static boolean isKeyDown(int key)
    {
        if (key < 0)
        {
            return Window.isMouseButtonPressed(-key);
        }

        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT)
        {
            return Window.isShiftPressed();
        }
        else if (key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL)
        {
            return Window.isCtrlPressed();
        }
        else if (key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT)
        {
            return Window.isAltPressed();
        }

        return Window.isKeyPressed(key);
    }

    public String getKeyCombo()
    {
        StringBuilder label = new StringBuilder(KeyCodes.getName(this.getMainKey()));

        for (int i = 1; i < this.keys.size(); i++)
        {
            label.insert(0, KeyCodes.getName(this.keys.get(i)) + " + ");
        }

        return label.toString();
    }

    public void copy(KeyCombo combo)
    {
        this.keys.clear();
        this.keys.addAll(combo.keys);
    }
}