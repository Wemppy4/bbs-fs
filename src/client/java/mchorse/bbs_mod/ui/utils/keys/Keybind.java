package mchorse.bbs_mod.ui.utils.keys;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Keybind class
 */
public class Keybind
{
    private IKey label;
    private IKey category;

    private KeyCombo combo;
    public Runnable callback;
    public boolean inside;
    public boolean strict;
    public Supplier<Boolean> active;

    public Keybind(KeyCombo combo, Runnable callback)
    {
        this.combo = combo;
        this.callback = callback;
    }

    public Keybind inside()
    {
        this.inside = true;

        return this;
    }

    /**
     * Require an exact modifier match: the keybind won't fire if a shift/ctrl/alt
     * key is held that isn't part of its combo. Use this on a plain-key bind that
     * would otherwise shadow a longer combo on the same key living in another
     * element (e.g. plain {@code T} vs {@code Shift + T}).
     */
    public Keybind strict()
    {
        this.strict = true;

        return this;
    }

    public Keybind active(Supplier<Boolean> active)
    {
        this.active = active;

        return this;
    }

    public Keybind label(IKey label)
    {
        this.label = label;

        return this;
    }

    public Keybind category(IKey category)
    {
        this.category = category;

        return this;
    }

    public int getScore()
    {
        return this.combo.keys.size();
    }

    public IKey getLabel()
    {
        return this.label == null ? this.combo.label : this.label;
    }

    public IKey getCategory()
    {
        return this.category == null ? this.combo.category : this.category;
    }

    public String getKeyCombo()
    {
        return this.combo.getKeyCombo();
    }

    public boolean check(int keyCode, KeyAction keyAction, boolean inside)
    {
        if (keyAction == KeyAction.REPEAT && !this.combo.repeatable)
        {
            return false;
        }

        if (keyCode != this.combo.getMainKey())
        {
            return false;
        }

        for (int i = 1; i < this.combo.keys.size(); i++)
        {
            if (!this.isKeyDown(this.combo.keys.get(i)))
            {
                return false;
            }
        }

        if (this.strict && this.hasExtraModifier())
        {
            return false;
        }

        return this.inside ? inside : true;
    }

    /**
     * Whether a modifier (shift/ctrl/alt) is currently held that the combo does
     * not list — used by {@link #strict} binds to step aside for longer combos.
     */
    private boolean hasExtraModifier()
    {
        if (Window.isShiftPressed() && !this.comboHas(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT))
        {
            return true;
        }

        if (Window.isCtrlPressed() && !this.comboHas(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL))
        {
            return true;
        }

        return Window.isAltPressed() && !this.comboHas(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private boolean comboHas(int left, int right)
    {
        return this.combo.keys.contains(left) || this.combo.keys.contains(right);
    }

    public boolean checkMouse(int mouseButton, boolean inside)
    {
        mouseButton = -mouseButton;

        if (mouseButton != this.combo.getMainKey())
        {
            return false;
        }

        for (int i = 1; i < this.combo.keys.size(); i++)
        {
            if (!this.isKeyDown(this.combo.keys.get(i)))
            {
                return false;
            }
        }

        return this.inside ? inside : true;
    }

    protected boolean isKeyDown(int key)
    {
        return KeyCombo.isKeyDown(key);
    }

    public boolean isActive()
    {
        return this.active == null || this.active.get();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Keybind)
        {
            Keybind keybind = (Keybind) obj;

            return Objects.equals(this.combo.keys, keybind.combo.keys) && this.inside == keybind.inside;
        }

        return super.equals(obj);
    }
}