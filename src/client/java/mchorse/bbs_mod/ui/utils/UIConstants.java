package mchorse.bbs_mod.ui.utils;

/**
 * Central UI dimension constants for a consistent, readable layout.
 * Minimal spacing to avoid "gigantism" in clip/form panels.
 */
public final class UIConstants
{
    /** Height of standard form controls: trackpad, button, textbox, keybind, color strip. */
    public static final int CONTROL_HEIGHT = 16;

    /** Base spacing between related elements (within a row, or label + control). */
    public static final int MARGIN = 3;

    /** Height of toggles and small switches. */
    public static final int TOGGLE_HEIGHT = 14;

    /** Padding inside scroll areas. */
    public static final int SCROLL_PADDING = 6;

    /** Gap between logical sections in clip/form panels. */
    public static final int SECTION_GAP = 3;

    /** Height of list items in dropdowns (e.g. bone list) for a compact list. */
    public static final int LIST_ITEM_HEIGHT = 14;

    private UIConstants()
    {}
}
