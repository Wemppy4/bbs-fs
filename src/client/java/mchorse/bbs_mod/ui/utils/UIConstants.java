package mchorse.bbs_mod.ui.utils;

/**
 * Central UI dimension constants for a consistent, readable layout.
 * Minimal spacing to avoid "gigantism" in clip/form panels.
 */
public final class UIConstants
{
    /** Height of standard form controls: trackpad, button, textbox, keybind, color strip. */
    public static final int CONTROL_HEIGHT = 16;

    /**
     * Size of an icon button: the 16px glyph with room around it — what a {@code UIIcon} is built at.
     * Too tall for a row of controls: an icon beside a button or a field is {@link UI#icon}.
     */
    public static final int ICON_SIZE = 20;

    /** Base spacing between related elements (within a row, or label + control). */
    public static final int MARGIN = 3;

    /** Height of toggles and small switches. */
    public static final int TOGGLE_HEIGHT = 14;

    /** Width of a switch with no label of its own: its track and the room around it. */
    public static final int SWITCH_WIDTH = 26;

    /** Padding inside scroll areas. */
    public static final int SCROLL_PADDING = 6;

    /** Gap between logical sections in clip/form panels. */
    public static final int SECTION_GAP = 3;

    /** Height of list items in dropdowns (e.g. bone list) for a compact list. */
    public static final int LIST_ITEM_HEIGHT = 14;

    /** Width of the fixed control column in a "label : control" row (see {@link UI#labelRow}); the name fills the rest. */
    public static final int VALUE_WIDTH = 90;

    private UIConstants()
    {}
}
