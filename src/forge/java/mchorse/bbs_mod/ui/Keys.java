package mchorse.bbs_mod.ui;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.graphics.window.InputCodes;

/**
 * IF THE KEYS DON'T APPEAR IN THE CONFIGURATION MENU, you used wrong constructor!
 * Use {@link KeyCombo#KeyCombo(String, IKey, int...)} intead of {@link KeyCombo#KeyCombo(IKey, int...)}!
 */
public class Keys
{
    public static final KeyCombo FILM_TRACK_SEARCH = new KeyCombo("track_search", L10n.lang("bbs.ui.film.track_search.title"), InputCodes.KEY_F, InputCodes.KEY_LEFT_CONTROL).categoryKey("film_controller");
    /* General */
    public static final KeyCombo DESELECT = new KeyCombo("deselect", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_DESELECT, InputCodes.KEY_D, InputCodes.KEY_LEFT_CONTROL);
    public static final KeyCombo KEYBINDS = new KeyCombo("keybinds", UIKeys.KEYS_LIST, InputCodes.KEY_F9);
    public static final KeyCombo NEXT = new KeyCombo("next", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_NEXT, InputCodes.KEY_RIGHT).repeatable();
    public static final KeyCombo PLAUSE = new KeyCombo("plause", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE, InputCodes.KEY_SPACE);
    public static final KeyCombo PREV = new KeyCombo("prev", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PREV, InputCodes.KEY_LEFT).repeatable();
    public static final KeyCombo REDO = new KeyCombo("redo", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_REDO, InputCodes.KEY_Y, InputCodes.KEY_LEFT_CONTROL);
    public static final KeyCombo UNDO = new KeyCombo("undo", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_UNDO, InputCodes.KEY_Z, InputCodes.KEY_LEFT_CONTROL);
    public static final KeyCombo COPY = new KeyCombo("copy", UIKeys.GENERAL_COPY, InputCodes.KEY_C, InputCodes.KEY_LEFT_CONTROL);
    public static final KeyCombo CUT = new KeyCombo("cut", UIKeys.GENERAL_CUT, InputCodes.KEY_X, InputCodes.KEY_LEFT_CONTROL);
    public static final KeyCombo PASTE = new KeyCombo("paste", UIKeys.GENERAL_PASTE, InputCodes.KEY_V, InputCodes.KEY_LEFT_CONTROL);
    public static final KeyCombo PRESETS = new KeyCombo("presets", UIKeys.GENERAL_PRESETS, InputCodes.KEY_V, InputCodes.KEY_LEFT_SHIFT, InputCodes.KEY_LEFT_CONTROL);
    public static final KeyCombo SAVE = new KeyCombo("save", UIKeys.GENERAL_SAVE, InputCodes.KEY_S, InputCodes.KEY_LEFT_CONTROL);
    public static final KeyCombo DELETE = new KeyCombo("delete", UIKeys.GENERAL_REMOVE, InputCodes.KEY_DELETE);
    public static final KeyCombo CONFIRM = new KeyCombo("confirm", UIKeys.GENERAL_CONFIRM, InputCodes.KEY_ENTER, InputCodes.KEY_LEFT_CONTROL);
    public static final KeyCombo CLOSE = new KeyCombo("close", UIKeys.GENERAL_CLOSE, InputCodes.KEY_ESCAPE);
    public static final KeyCombo UI_SCALE_INC = new KeyCombo("ui_scale_inc", UIKeys.KEYS_UI_SCALE_INC, InputCodes.KEY_EQUAL, InputCodes.KEY_LEFT_CONTROL).repeatable();
    public static final KeyCombo UI_SCALE_DEC = new KeyCombo("ui_scale_dec", UIKeys.KEYS_UI_SCALE_DEC, InputCodes.KEY_MINUS, InputCodes.KEY_LEFT_CONTROL).repeatable();

    /* Camera editor */
    public static final KeyCombo ADD_AT_CURSOR = new KeyCombo("add_at_cursor", UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_AT_CURSOR, InputCodes.KEY_A, InputCodes.KEY_LEFT_SHIFT).categoryKey("camera");
    public static final KeyCombo ADD_AT_TICK = new KeyCombo("add_at_tick", UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_AT_TICK, InputCodes.KEY_A, InputCodes.KEY_LEFT_ALT).categoryKey("camera");
    public static final KeyCombo ADD_ON_TOP = new KeyCombo("add_on_top", UIKeys.CAMERA_TIMELINE_CONTEXT_ADD_ON_TOP, InputCodes.KEY_A, InputCodes.KEY_LEFT_SHIFT, InputCodes.KEY_LEFT_ALT).categoryKey("camera");
    public static final KeyCombo CLIP_CUT = new KeyCombo("clip_cut", UIKeys.CAMERA_TIMELINE_CONTEXT_CUT, InputCodes.KEY_C, InputCodes.KEY_LEFT_ALT).categoryKey("camera");
    public static final KeyCombo CLIP_DURATION = new KeyCombo("clip_duration", UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT_DURATION, InputCodes.KEY_M).categoryKey("camera");
    public static final KeyCombo CLIP_ENABLE = new KeyCombo("clip_enable", UIKeys.CAMERA_TIMELINE_KEYS_ENABLED, InputCodes.KEY_J).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_ALL = new KeyCombo("clip_select_all", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_SELECT_ALL, InputCodes.KEY_A, InputCodes.KEY_LEFT_CONTROL).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_TRACK = new KeyCombo("clip_select_track", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_SELECT_TRACK, InputCodes.KEY_A, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_TRACK_BEFORE = new KeyCombo("clip_select_track_before", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_SELECT_TRACK_BEFORE, InputCodes.KEY_COMMA, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_TRACK_AFTER = new KeyCombo("clip_select_track_after", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_SELECT_TRACK_AFTER, InputCodes.KEY_PERIOD, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT).categoryKey("camera");
    public static final KeyCombo CLIP_LAYER_UP = new KeyCombo("clip_layer_up", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_LAYER_UP, InputCodes.KEY_UP, InputCodes.KEY_LEFT_ALT).repeatable().categoryKey("camera");
    public static final KeyCombo CLIP_LAYER_DOWN = new KeyCombo("clip_layer_down", UIKeys.CAMERA_EDITOR_KEYS_CLIPS_LAYER_DOWN, InputCodes.KEY_DOWN, InputCodes.KEY_LEFT_ALT).repeatable().categoryKey("camera");
    public static final KeyCombo CLIP_SHIFT = new KeyCombo("clip_shift", UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT, InputCodes.KEY_S, InputCodes.KEY_LEFT_SHIFT).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_AFTER = new KeyCombo("clip_select_after", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_SELECT_AFTER, InputCodes.KEY_PERIOD, InputCodes.KEY_LEFT_CONTROL).categoryKey("camera");
    public static final KeyCombo CLIP_SELECT_BEFORE = new KeyCombo("clip_select_before", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_SELECT_BEFORE, InputCodes.KEY_COMMA, InputCodes.KEY_LEFT_CONTROL).categoryKey("camera");
    public static final KeyCombo FLIGHT = new KeyCombo("flight", UIKeys.CAMERA_EDITOR_KEYS_MODES_FLIGHT, InputCodes.KEY_F).categoryKey("camera");
    public static final KeyCombo LOOPING = new KeyCombo("looping", UIKeys.CAMERA_EDITOR_KEYS_MODES_LOOPING, InputCodes.KEY_L).categoryKey("camera");
    public static final KeyCombo LOOPING_SET_MAX = new KeyCombo("looping_set_max", UIKeys.CAMERA_EDITOR_KEYS_LOOPING_SET_MAX, InputCodes.KEY_RIGHT_BRACKET).categoryKey("camera");
    public static final KeyCombo LOOPING_SET_MIN = new KeyCombo("looping_set_min", UIKeys.CAMERA_EDITOR_KEYS_LOOPING_SET_MIN, InputCodes.KEY_LEFT_BRACKET).categoryKey("camera");
    public static final KeyCombo NEXT_CLIP = new KeyCombo("next_clip", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_NEXT_CLIP, InputCodes.KEY_RIGHT, InputCodes.KEY_LEFT_SHIFT).repeatable().categoryKey("camera");
    public static final KeyCombo PREV_CLIP = new KeyCombo("prev_clip", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PREV_CLIP, InputCodes.KEY_LEFT, InputCodes.KEY_LEFT_SHIFT).repeatable().categoryKey("camera");
    public static final KeyCombo JUMP_FORWARD = new KeyCombo("jump_forward", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_JUMP_FORWARD, InputCodes.KEY_UP).repeatable().categoryKey("camera");
    public static final KeyCombo JUMP_BACKWARD = new KeyCombo("jump_backward", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_JUMP_BACKWARD, InputCodes.KEY_DOWN).repeatable().categoryKey("camera");
    public static final KeyCombo FADE_IN = new KeyCombo("fade_in", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_FADE_IN, InputCodes.KEY_COMMA).categoryKey("camera");
    public static final KeyCombo FADE_OUT = new KeyCombo("fade_out", UIKeys.CAMERA_EDITOR_KEYS_EDITOR_FADE_OUT, InputCodes.KEY_PERIOD).categoryKey("camera");
    public static final KeyCombo MARKER_ADD = new KeyCombo("marker_add", UIKeys.FILM_MARKERS_KEYS_ADD, InputCodes.KEY_M, InputCodes.KEY_LEFT_CONTROL).categoryKey("camera");
    public static final KeyCombo MARKER_NEXT = new KeyCombo("marker_next", UIKeys.FILM_MARKERS_KEYS_NEXT, InputCodes.KEY_RIGHT, InputCodes.KEY_LEFT_CONTROL).repeatable().categoryKey("camera");
    public static final KeyCombo MARKER_PREV = new KeyCombo("marker_prev", UIKeys.FILM_MARKERS_KEYS_PREV, InputCodes.KEY_LEFT, InputCodes.KEY_LEFT_CONTROL).repeatable().categoryKey("camera");

    /* Flight mode keybinds */
    public static final KeyCombo FLIGHT_FORWARD = new KeyCombo("flight_forward", UIKeys.CAMERA_FLIGHT_FLIGHT_FORWARD, InputCodes.KEY_W).categoryKey("flight");
    public static final KeyCombo FLIGHT_BACKWARD = new KeyCombo("flight_backward", UIKeys.CAMERA_FLIGHT_FLIGHT_BACKWARD, InputCodes.KEY_S).categoryKey("flight");
    public static final KeyCombo FLIGHT_LEFT = new KeyCombo("flight_left", UIKeys.CAMERA_FLIGHT_FLIGHT_LEFT, InputCodes.KEY_A).categoryKey("flight");
    public static final KeyCombo FLIGHT_RIGHT = new KeyCombo("flight_right", UIKeys.CAMERA_FLIGHT_FLIGHT_RIGHT, InputCodes.KEY_D).categoryKey("flight");
    public static final KeyCombo FLIGHT_UP = new KeyCombo("flight_up", UIKeys.CAMERA_FLIGHT_FLIGHT_UP, InputCodes.KEY_SPACE).categoryKey("flight");
    public static final KeyCombo FLIGHT_DOWN = new KeyCombo("flight_down", UIKeys.CAMERA_FLIGHT_FLIGHT_DOWN, InputCodes.KEY_LEFT_SHIFT).categoryKey("flight");
    public static final KeyCombo FLIGHT_TILT_UP = new KeyCombo("flight_tilt_up", UIKeys.CAMERA_FLIGHT_FLIGHT_TILT_UP, InputCodes.KEY_UP).categoryKey("flight");
    public static final KeyCombo FLIGHT_TILT_DOWN = new KeyCombo("flight_tilt_down", UIKeys.CAMERA_FLIGHT_FLIGHT_TILT_DOWN, InputCodes.KEY_DOWN).categoryKey("flight");
    public static final KeyCombo FLIGHT_PAN_LEFT = new KeyCombo("flight_pan_left", UIKeys.CAMERA_FLIGHT_FLIGHT_PAN_LEFT, InputCodes.KEY_LEFT).categoryKey("flight");
    public static final KeyCombo FLIGHT_PAN_RIGHT = new KeyCombo("flight_pan_right", UIKeys.CAMERA_FLIGHT_FLIGHT_PAN_RIGHT, InputCodes.KEY_RIGHT).categoryKey("flight");

    /* Dashboard */
    public static final KeyCombo OPEN_UTILITY_PANEL = new KeyCombo("utility_panel", UIKeys.UTILITY_TITLE, InputCodes.KEY_F6).categoryKey("dashboard");
    public static final KeyCombo OPEN_SETTINGS = new KeyCombo("settings", UIKeys.CONFIG_TITLE, InputCodes.KEY_S, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_ALT).categoryKey("dashboard");
    public static final KeyCombo OPEN_DATA_MANAGER = new KeyCombo("data_manager", UIKeys.PANELS_KEYS_OPEN_DATA_MANAGER, InputCodes.KEY_N).categoryKey("dashboard");
    public static final KeyCombo OPEN_NEW_TAB = new KeyCombo("new_tab", UIKeys.PANELS_KEYS_OPEN_NEW_TAB, InputCodes.KEY_N, InputCodes.KEY_LEFT_CONTROL).categoryKey("dashboard");
    public static final KeyCombo TOGGLE_VISIBILITY = new KeyCombo("toggle", UIKeys.DASHBOARD_CONTEXT_TOGGLE_VISIBILITY, InputCodes.KEY_F1).categoryKey("dashboard");
    public static final KeyCombo TOGGLE_DEBUG = new KeyCombo("toggle_debug", UIKeys.DASHBOARD_KEYS_TOGGLE_DEBUG, InputCodes.KEY_F5).categoryKey("dashboard");

    /* Forms */
    public static final KeyCombo FORMS_FOCUS = new KeyCombo("focus", UIKeys.FORMS_LIST_CONTEXT_FOCUS, InputCodes.KEY_F, InputCodes.KEY_LEFT_CONTROL).categoryKey("forms");
    public static final KeyCombo FORMS_PICK = new KeyCombo("pick", UIKeys.GENERAL_PICK, InputCodes.KEY_P).categoryKey("forms");
    public static final KeyCombo FORMS_EDIT = new KeyCombo("edit", UIKeys.GENERAL_EDIT, InputCodes.KEY_E).categoryKey("forms");
    public static final KeyCombo FORMS_PICK_ALT = new KeyCombo("pick_alt", UIKeys.GENERAL_PICK, InputCodes.KEY_P, InputCodes.KEY_LEFT_SHIFT).categoryKey("forms");
    public static final KeyCombo FORMS_EDIT_ALT = new KeyCombo("edit_alt", UIKeys.GENERAL_EDIT, InputCodes.KEY_E, InputCodes.KEY_LEFT_SHIFT).categoryKey("forms");
    public static final KeyCombo FORMS_PICK_TEXTURE = new KeyCombo("pick_texture", UIKeys.FORMS_EDITOR_MODEL_PICK_TEXTURE, InputCodes.KEY_P, InputCodes.KEY_LEFT_SHIFT).categoryKey("forms");
    public static final KeyCombo FORMS_OPEN_STATES_EDITOR = new KeyCombo("open_states_editor", UIKeys.FORMS_EDITOR_STATES_OPEN, InputCodes.KEY_BACKSLASH).categoryKey("forms");
    public static final KeyCombo FORMS_TOGGLE_BODY_PART_GIZMO = new KeyCombo("toggle_body_part_gizmo", UIKeys.FORMS_EDITOR_BODY_PART_GIZMO, InputCodes.KEY_Q, InputCodes.KEY_LEFT_CONTROL).categoryKey("forms");
    public static final KeyCombo FORMS_CENTER_CAMERA = new KeyCombo("center_camera", UIKeys.FORMS_EDITOR_KEYS_CENTER_CAMERA, InputCodes.KEY_C).categoryKey("forms");

    /* Pixel editor */
    public static final KeyCombo PIXEL_SWAP = new KeyCombo("swap", UIKeys.TEXTURES_KEYS_SWAP, InputCodes.KEY_X).categoryKey("pixels");
    public static final KeyCombo PIXEL_PICK = new KeyCombo("pick", UIKeys.TEXTURES_KEYS_PICK, InputCodes.KEY_R).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_BRUSH = new KeyCombo("tool_brush", UIKeys.TEXTURES_KEYS_TOOL_BRUSH, InputCodes.KEY_B).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_ERASER = new KeyCombo("tool_eraser", UIKeys.TEXTURES_KEYS_TOOL_ERASER, InputCodes.KEY_E).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_MOVE = new KeyCombo("tool_move", UIKeys.TEXTURES_KEYS_TOOL_MOVE, InputCodes.KEY_V).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_FILL = new KeyCombo("tool_fill", UIKeys.TEXTURES_KEYS_TOOL_FILL, InputCodes.KEY_F).categoryKey("pixels");
    public static final KeyCombo PIXEL_TOOL_SELECTION = new KeyCombo("tool_selection", UIKeys.TEXTURES_TOOLS_SELECTION, InputCodes.KEY_M).categoryKey("pixels");
    public static final KeyCombo PIXEL_DESELECT = new KeyCombo("deselect", UIKeys.TEXTURES_DESELECT, InputCodes.KEY_D, InputCodes.KEY_LEFT_CONTROL).categoryKey("pixels");
    public static final KeyCombo PIXEL_COPY_HEX = new KeyCombo("copy_hex", UIKeys.TEXTURES_VIEWER_CONTEXT_COPY_HEX, InputCodes.KEY_C, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT).categoryKey("pixels");
    public static final KeyCombo PIXEL_BRUSH_DEC = new KeyCombo("brush_dec", UIKeys.TEXTURES_KEYS_BRUSH_DEC, InputCodes.KEY_LEFT_BRACKET).repeatable().categoryKey("pixels");
    public static final KeyCombo PIXEL_BRUSH_INC = new KeyCombo("brush_inc", UIKeys.TEXTURES_KEYS_BRUSH_INC, InputCodes.KEY_RIGHT_BRACKET).repeatable().categoryKey("pixels");
    public static final KeyCombo PIXEL_FRAME_PREV = new KeyCombo("frame_prev", UIKeys.TEXTURES_KEYS_FRAME_PREV, InputCodes.KEY_COMMA).repeatable().categoryKey("pixels");
    public static final KeyCombo PIXEL_FRAME_NEXT = new KeyCombo("frame_next", UIKeys.TEXTURES_KEYS_FRAME_NEXT, InputCodes.KEY_PERIOD).repeatable().categoryKey("pixels");
    public static final KeyCombo PIXEL_FRAME_FIRST = new KeyCombo("frame_first", UIKeys.TEXTURES_KEYS_FRAME_FIRST, InputCodes.KEY_COMMA, InputCodes.KEY_LEFT_SHIFT).categoryKey("pixels");
    public static final KeyCombo PIXEL_FRAME_LAST = new KeyCombo("frame_last", UIKeys.TEXTURES_KEYS_FRAME_LAST, InputCodes.KEY_PERIOD, InputCodes.KEY_LEFT_SHIFT).categoryKey("pixels");
    public static final KeyCombo PIXEL_FRAME_ADD = new KeyCombo("frame_add", UIKeys.TEXTURES_KEYS_FRAME_ADD, InputCodes.KEY_N, InputCodes.KEY_LEFT_ALT).categoryKey("pixels");
    public static final KeyCombo PIXEL_FRAME_ADD_EMPTY = new KeyCombo("frame_add_empty", UIKeys.TEXTURES_KEYS_FRAME_ADD_EMPTY, InputCodes.KEY_N, InputCodes.KEY_LEFT_ALT, InputCodes.KEY_LEFT_SHIFT).categoryKey("pixels");
    public static final KeyCombo PIXEL_FRAME_PLAY = new KeyCombo("frame_play", UIKeys.TEXTURES_FRAMES_PLAY, InputCodes.KEY_SPACE).categoryKey("pixels");
    public static final KeyCombo PIXEL_CLEAR = new KeyCombo("clear", UIKeys.TEXTURES_KEYS_CLEAR, InputCodes.KEY_DELETE).categoryKey("pixels");
    public static final KeyCombo PIXEL_FLIP_H = new KeyCombo("flip_h", UIKeys.TEXTURES_KEYS_FLIP_H, InputCodes.KEY_H, InputCodes.KEY_LEFT_SHIFT).categoryKey("pixels");
    public static final KeyCombo PIXEL_FLIP_V = new KeyCombo("flip_v", UIKeys.TEXTURES_KEYS_FLIP_V, InputCodes.KEY_V, InputCodes.KEY_LEFT_SHIFT).categoryKey("pixels");

    /* Keyframes */
    public static final KeyCombo KEYFRAMES_INSERT = new KeyCombo("insert", UIKeys.KEYFRAMES_KEYS_INSERT, InputCodes.KEY_I).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_ENABLE = new KeyCombo("keyframes_enable", UIKeys.KEYFRAMES_KEYS_ENABLED, InputCodes.KEY_J).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_FIT_SELECTED = new KeyCombo("fit_selected", UIKeys.KEYFRAMES_GRAPH_FIT_SELECTED, InputCodes.KEY_HOME, InputCodes.KEY_LEFT_SHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_MAXIMIZE = new KeyCombo("maximize", UIKeys.KEYFRAMES_CONTEXT_MAXIMIZE, InputCodes.KEY_HOME).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_ALL = new KeyCombo("select_all", UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, InputCodes.KEY_A, InputCodes.KEY_LEFT_CONTROL).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_TRACK = new KeyCombo("select_track", UIKeys.KEYFRAMES_KEYS_SELECT_TRACK, InputCodes.KEY_A, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_TRACK_LEFT = new KeyCombo("select_track_left", UIKeys.KEYFRAMES_KEYS_SELECT_TRACK_LEFT, InputCodes.KEY_COMMA, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_TRACK_RIGHT = new KeyCombo("select_track_right", UIKeys.KEYFRAMES_KEYS_SELECT_TRACK_RIGHT, InputCodes.KEY_PERIOD, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_INTERP = new KeyCombo("interp", UIKeys.KEYFRAMES_KEYS_TOGGLE_INTERP, InputCodes.KEY_T, InputCodes.KEY_LEFT_SHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_LEFT = new KeyCombo("select_left", UIKeys.KEYFRAMES_KEYS_SELECT_LEFT, InputCodes.KEY_COMMA, InputCodes.KEY_LEFT_CONTROL).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_RIGHT = new KeyCombo("select_right", UIKeys.KEYFRAMES_KEYS_SELECT_RIGHT, InputCodes.KEY_PERIOD, InputCodes.KEY_LEFT_CONTROL).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_SAME = new KeyCombo("select_same", UIKeys.KEYFRAMES_KEYS_SELECT_SAME, InputCodes.KEY_L).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SCALE_TIME = new KeyCombo("scale_time", UIKeys.KEYFRAMES_KEYS_SCALE_TIME, InputCodes.KEY_V).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_STACK_KEYFRAMES = new KeyCombo("stack_keyframes", UIKeys.KEYFRAMES_KEYS_STACK_KEYFRAMES, InputCodes.KEY_B).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_PREV = new KeyCombo("select_prev", UIKeys.KEYFRAMES_KEYS_SELECT_PREV, InputCodes.KEY_LEFT_BRACKET).repeatable().categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SELECT_NEXT = new KeyCombo("select_next", UIKeys.KEYFRAMES_KEYS_SELECT_NEXT, InputCodes.KEY_RIGHT_BRACKET).repeatable().categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_SPREAD = new KeyCombo("spread", UIKeys.KEYFRAMES_CONTEXT_SPREAD, InputCodes.KEY_B, InputCodes.KEY_LEFT_ALT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_ADJUST_VALUES = new KeyCombo("adjust_values", UIKeys.KEYFRAMES_CONTEXT_ADJUST_VALUES, InputCodes.KEY_N, InputCodes.KEY_LEFT_SHIFT).categoryKey("keyframes");
    public static final KeyCombo KEYFRAMES_FLIP = new KeyCombo("flip", UIKeys.KEYFRAMES_CONTEXT_FLIP, InputCodes.KEY_F, InputCodes.KEY_LEFT_ALT).categoryKey("keyframes");

    /* World menu */
    public static final KeyCombo CYCLE_PANELS = new KeyCombo("cycle_panels", UIKeys.WORLD_KEYS_CYCLE_PANELS, InputCodes.KEY_TAB).categoryKey("world");

    /* Transformations */
    public static final KeyCombo TRANSFORMATIONS_TRANSLATE = new KeyCombo("translate", UIKeys.TRANSFORMS_TRANSLATE, InputCodes.KEY_G).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_SCALE = new KeyCombo("scale", UIKeys.TRANSFORMS_SCALE, InputCodes.KEY_S).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_ROTATE = new KeyCombo("rotate", UIKeys.TRANSFORMS_ROTATE, InputCodes.KEY_R).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_RESET = new KeyCombo("reset", UIKeys.TRANSFORMS_CONTEXT_RESET, InputCodes.KEY_R, InputCodes.KEY_LEFT_SHIFT).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_X = new KeyCombo("x", UIKeys.GENERAL_X, InputCodes.KEY_X).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_Y = new KeyCombo("y", UIKeys.GENERAL_Y, InputCodes.KEY_Y).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_Z = new KeyCombo("z", UIKeys.GENERAL_Z, InputCodes.KEY_Z).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_TOGGLE_AXES = new KeyCombo("toggle_axes", UIKeys.TRANSFORMS_KEYS_TOGGLE_AXES, InputCodes.KEY_F8).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_HIDE_GIZMO = new KeyCombo("hide_gizmo", UIKeys.TRANSFORMS_KEYS_HIDE_GIZMO, InputCodes.KEY_LEFT_ALT).categoryKey("transformations");
    /* The combo id is the settings key a custom binding is stored under, so it stays
     * "toggle_local" — the name this key had when it toggled the local flag — even
     * though it now opens the space list. Renaming it would silently reset everyone's
     * rebound key to the default. */
    public static final KeyCombo TRANSFORMATIONS_SPACE_MENU = new KeyCombo("toggle_local", UIKeys.TRANSFORMS_SPACE_OPEN, InputCodes.KEY_Q).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_ROTATION_MODE = new KeyCombo("rotation_mode", UIKeys.TRANSFORMS_KEYS_ROTATION_MODE, InputCodes.KEY_Q, InputCodes.KEY_LEFT_SHIFT).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_TOGGLE_FIX = new KeyCombo("toggle_fix", UIKeys.TRANSFORMS_KEYS_TOGGLE_FIX, InputCodes.KEY_Y).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_MIRROR_EDIT = new KeyCombo("mirror_edit", UIKeys.TRANSFORMS_KEYS_MIRROR_EDIT, InputCodes.KEY_F, InputCodes.KEY_LEFT_SHIFT).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_COPY_WORLD = new KeyCombo("copy_world", UIKeys.TRANSFORMS_CONTEXT_COPY_WORLD, InputCodes.KEY_C, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT, InputCodes.KEY_LEFT_ALT).categoryKey("transformations");
    public static final KeyCombo TRANSFORMATIONS_PASTE_WORLD = new KeyCombo("paste_world", UIKeys.TRANSFORMS_CONTEXT_PASTE_WORLD, InputCodes.KEY_V, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT, InputCodes.KEY_LEFT_ALT).categoryKey("transformations");

    /* Film controller */
    public static final KeyCombo FILM_CONTROLLER_START_RECORDING = new KeyCombo("start_recording", UIKeys.FILM_CONTROLLER_KEYS_START_RECORDING, InputCodes.KEY_R, InputCodes.KEY_LEFT_CONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_INSERT_FRAME = new KeyCombo("insert_frame", UIKeys.FILM_CONTROLLER_KEYS_INSERT_FRAME, InputCodes.KEY_I).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_CONTROL = new KeyCombo("toggle_control", UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_CONTROL, InputCodes.KEY_H).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_ORBIT_MODE = new KeyCombo("toggle_orbit_mode", UIKeys.FILM_CONTROLLER_KEYS_CHANGE_CAMERA_MODE, InputCodes.KEY_F3).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_ORTHO = new KeyCombo("toggle_ortho", UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORTHO, InputCodes.KEY_KP_5).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TELEPORT_ORBIT = new KeyCombo("teleport_orbit_record", UIKeys.FILM_CONTROLLER_KEYS_TELEPORT_ORBIT, InputCodes.KEY_C).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_ATTACH_ORBIT = new KeyCombo("attach_orbit_record", UIKeys.FILM_CONTROLLER_KEYS_ATTACH_ORBIT, InputCodes.KEY_Z, InputCodes.KEY_LEFT_SHIFT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_REPLAY_MENU = new KeyCombo("toggle_replay_menu", UIKeys.FILM_CONTROLLER_KEYS_OPEN_REPLAYS, InputCodes.KEY_F4).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_MOVE_REPLAY_TO_CURSOR = new KeyCombo("move_replay_to_cursor", UIKeys.FILM_CONTROLLER_KEYS_MOVE_REPLAY_TO_CURSOR, InputCodes.KEY_G, InputCodes.KEY_LEFT_CONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_RESTART_ACTIONS = new KeyCombo("restart_actions", UIKeys.FILM_CONTROLLER_KEYS_RESTART_ACTIONS, InputCodes.KEY_R, InputCodes.KEY_LEFT_ALT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_ONION_SKIN = new KeyCombo("toggle_onion_skin", UIKeys.FILM_CONTROLLER_ONION_SKIN_KEYS_TOGGLE, InputCodes.KEY_O).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_MOTION_PATH = new KeyCombo("toggle_motion_path", UIKeys.FILM_CONTROLLER_MOTION_PATH_KEYS_TOGGLE, InputCodes.KEY_M).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_MOTION_PATH_PIN = new KeyCombo("toggle_motion_path_pin", UIKeys.FILM_CONTROLLER_MOTION_PATH_KEYS_TOGGLE_PIN, InputCodes.KEY_M, InputCodes.KEY_LEFT_SHIFT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_OPEN_REPLAYS = new KeyCombo("toggle_replays", UIKeys.FILM_CONTROLLER_KEYS_OPEN_REPLAYS, InputCodes.KEY_RIGHT_SHIFT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_CYCLE_EDITORS = new KeyCombo("cycle_editors", UIKeys.FILM_CONTROLLER_KEYS_CYCLE_EDITORS, InputCodes.KEY_GRAVE_ACCENT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_TOGGLE_ACTIONS = new KeyCombo("toggle_actions", UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ACTIONS, InputCodes.KEY_GRAVE_ACCENT, InputCodes.KEY_LEFT_SHIFT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_NEXT_DOCK_TAB = new KeyCombo("next_dock_tab", UIKeys.FILM_CONTROLLER_KEYS_CYCLE_EDITORS, InputCodes.KEY_TAB, InputCodes.KEY_LEFT_CONTROL).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_PREV_DOCK_TAB = new KeyCombo("prev_dock_tab", UIKeys.FILM_CONTROLLER_KEYS_CYCLE_EDITORS, InputCodes.KEY_TAB, InputCodes.KEY_LEFT_SHIFT, InputCodes.KEY_LEFT_CONTROL).categoryKey("film_controller");
    public static final KeyCombo DOCK_MAXIMIZE = new KeyCombo("dock_maximize", UIKeys.DOCK_KEYS_MAXIMIZE, InputCodes.KEY_SPACE, InputCodes.KEY_LEFT_CONTROL).categoryKey("film_controller");
    public static final KeyCombo DOCK_UNDO_LAYOUT = new KeyCombo("dock_undo_layout", UIKeys.DOCK_UNDO_LAYOUT, InputCodes.KEY_Z, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_ALT).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_PREV_REPLAY = new KeyCombo("prev_replay", UIKeys.FILM_CONTROLLER_KEYS_PREV_REPLAY, InputCodes.KEY_PAGE_UP).categoryKey("film_controller");
    public static final KeyCombo FILM_CONTROLLER_NEXT_REPLAY = new KeyCombo("next_replay", UIKeys.FILM_CONTROLLER_KEYS_NEXT_REPLAY, InputCodes.KEY_PAGE_DOWN).categoryKey("film_controller");

    /* Replays editor */
    public static final KeyCombo REPLAYS_TAB_1 = new KeyCombo("tab_1", UIKeys.FILM_REPLAY_TAB_1, InputCodes.KEY_1).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_TAB_2 = new KeyCombo("tab_2", UIKeys.FILM_REPLAY_TAB_2, InputCodes.KEY_2).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_TAB_3 = new KeyCombo("tab_3", UIKeys.FILM_REPLAY_TAB_3, InputCodes.KEY_3).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_TAB_4 = new KeyCombo("tab_4", UIKeys.FILM_REPLAY_TAB_4, InputCodes.KEY_4).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_TAB_5 = new KeyCombo("tab_5", UIKeys.FILM_REPLAY_TAB_5, InputCodes.KEY_5).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_DUPE = new KeyCombo("replays_dupe", UIKeys.SCENE_REPLAYS_CONTEXT_DUPE, InputCodes.KEY_D, InputCodes.KEY_LEFT_CONTROL).categoryKey("replays_editor");
    public static final KeyCombo REPLAYS_SELECT_ALL = new KeyCombo("replays_select_all", UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, InputCodes.KEY_A, InputCodes.KEY_LEFT_CONTROL).categoryKey("replays_editor");

    /* Recording groups */
    public static final KeyCombo RECORDING_GROUP_ALL = new KeyCombo("all", UIKeys.FILM_GROUPS_ALL, InputCodes.KEY_1).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_LEFT_STICK = new KeyCombo("left_stick", UIKeys.FILM_GROUPS_LEFT_STICK, InputCodes.KEY_2).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_RIGHT_STICK = new KeyCombo("right_stick", UIKeys.FILM_GROUPS_RIGHT_STICK, InputCodes.KEY_3).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_TRIGGERS = new KeyCombo("triggers", UIKeys.FILM_GROUPS_TRIGGERS, InputCodes.KEY_4).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_EXTRA_1 = new KeyCombo("extra_1", UIKeys.FILM_GROUPS_EXTRA_1, InputCodes.KEY_5).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_EXTRA_2 = new KeyCombo("extra_2", UIKeys.FILM_GROUPS_EXTRA_2, InputCodes.KEY_6).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_ONLY_POSITION = new KeyCombo("only_position", UIKeys.FILM_GROUPS_ONLY_POSITION, InputCodes.KEY_7).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_ONLY_ROTATION = new KeyCombo("only_rotation", UIKeys.FILM_GROUPS_ONLY_ROTATION, InputCodes.KEY_8).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_POS_ROT = new KeyCombo("pos_rot", UIKeys.FILM_GROUPS_ONLY_POS_ROT, InputCodes.KEY_9).categoryKey("recording_groups");
    public static final KeyCombo RECORDING_GROUP_OUTSIDE = new KeyCombo("outside", UIKeys.FILM_GROUPS_OUTSIDE, InputCodes.KEY_R).categoryKey("recording_groups");

    /* Model block editor */
    public static final KeyCombo MODEL_BLOCKS_MOVE_TO = new KeyCombo("move_to", UIKeys.MODEL_BLOCKS_KEYS_MOVE_TO, InputCodes.KEY_G, InputCodes.KEY_LEFT_CONTROL).categoryKey("model_blocks");
    public static final KeyCombo MODEL_BLOCKS_TOGGLE_RENDERING = new KeyCombo("toggle_rendering", UIKeys.MODEL_BLOCKS_KEYS_TOGGLE_RENDERING, InputCodes.KEY_F7).categoryKey("model_blocks");
    public static final KeyCombo MODEL_BLOCKS_TELEPORT = new KeyCombo("teleport", UIKeys.MODEL_BLOCKS_KEYS_TELEPORT, InputCodes.KEY_T, InputCodes.KEY_LEFT_CONTROL).categoryKey("model_blocks");
    public static final KeyCombo MODEL_BLOCKS_TELEPORT_ORBIT = new KeyCombo("teleport_orbit_block", UIKeys.MODEL_BLOCKS_KEYS_TELEPORT_ORBIT, InputCodes.KEY_C).categoryKey("model_blocks");

    /* Model editor */
    public static final KeyCombo MODEL_EDITOR_NEXT_TAB = new KeyCombo("next_tab", UIKeys.MODEL_EDITOR_KEYS_CYCLE_TABS, InputCodes.KEY_TAB, InputCodes.KEY_LEFT_CONTROL).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_PREV_TAB = new KeyCombo("prev_tab", UIKeys.MODEL_EDITOR_KEYS_CYCLE_TABS, InputCodes.KEY_TAB, InputCodes.KEY_LEFT_SHIFT, InputCodes.KEY_LEFT_CONTROL).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_EXPAND_ALL = new KeyCombo("expand_all", UIKeys.MODEL_EDITOR_KEYS_EXPAND_ALL, InputCodes.KEY_E, InputCodes.KEY_LEFT_CONTROL).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_COLLAPSE_ALL = new KeyCombo("collapse_all", UIKeys.MODEL_EDITOR_KEYS_COLLAPSE_ALL, InputCodes.KEY_E, InputCodes.KEY_LEFT_CONTROL, InputCodes.KEY_LEFT_SHIFT).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_FIND_BONE = new KeyCombo("find_bone", UIKeys.MODEL_EDITOR_KEYS_FIND_BONE, InputCodes.KEY_F, InputCodes.KEY_LEFT_CONTROL).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_OPEN_HISTORY = new KeyCombo("open_history", UIKeys.MODEL_EDITOR_OPEN_HISTORY, InputCodes.KEY_H, InputCodes.KEY_LEFT_CONTROL).categoryKey("model_editor");

    /* The model tree's verbs, on the picked rows whatever they are. Removing is the shared Delete,
     * as in every other list. Adding a group is Shift+A, the camera timeline's "add to this list"
     * (Ctrl+N is the dashboard's new tab), and a cube goes on the letter of its own. */
    public static final KeyCombo MODEL_EDITOR_GROUP_ADD = new KeyCombo("group_add", UIKeys.MODEL_EDITOR_MODEL_GROUP_ADD, InputCodes.KEY_A, InputCodes.KEY_LEFT_SHIFT).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_CUBE_ADD = new KeyCombo("cube_add", UIKeys.MODEL_EDITOR_MODEL_CUBE_ADD, InputCodes.KEY_C, InputCodes.KEY_LEFT_SHIFT).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_GROUP_DUPE = new KeyCombo("group_dupe", UIKeys.MODEL_EDITOR_MODEL_DUPLICATE, InputCodes.KEY_D, InputCodes.KEY_LEFT_CONTROL).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_GROUP_RENAME = new KeyCombo("group_rename", UIKeys.MODEL_EDITOR_MODEL_GROUP_RENAME, InputCodes.KEY_F2).categoryKey("model_editor");
    public static final KeyCombo MODEL_EDITOR_GROUP_IK_BONES = new KeyCombo("group_ik_bones", UIKeys.MODEL_EDITOR_MODEL_GROUP_IK_BONES, InputCodes.KEY_I, InputCodes.KEY_LEFT_CONTROL).categoryKey("model_editor");

    /* What the gizmo moves, geometry or the pivot alone: P, Blockbench's pivot tool. Nothing else
     * the model editor shows takes a bare P. */
    public static final KeyCombo MODEL_EDITOR_PIVOT_ONLY = new KeyCombo("pivot_only", UIKeys.MODEL_EDITOR_MODEL_PIVOT_ONLY, InputCodes.KEY_P).categoryKey("model_editor");

    /* Texture picker */
    public static final KeyCombo TEXTURE_PICKER_FIND = new KeyCombo("find", UIKeys.TEXTURE_KEYS_FIND_ALL, InputCodes.KEY_F, InputCodes.KEY_LEFT_CONTROL).categoryKey("texture_picker");
}
