package mchorse.bbs_mod;

import java.util.HashSet;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueLinkList;
import mchorse.bbs_mod.settings.values.core.ValueRecentData;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
import mchorse.bbs_mod.settings.values.ui.ValueKeyframeStyle;
import mchorse.bbs_mod.settings.values.ui.ValueLanguage;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.settings.values.ui.ValuePhysicsDebug;
import mchorse.bbs_mod.settings.values.ui.ValueOrder;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.settings.values.ui.ValueTrackStyles;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.colors.Oklab;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeStyle;

public class BBSSettings {

	public static final String DEFAULT_FFMPEG_ARGUMENTS = "-f rawvideo -pix_fmt bgr24 -s %WIDTH%x%HEIGHT% -r %FPS% -i - -vf %FILTERS% -c:v libx264 -preset ultrafast -tune zerolatency -qp 18 -pix_fmt yuv420p %NAME%.mp4";
	public static final String DEFAULT_AUDIO_FFMPEG_ARGUMENTS = "-f rawvideo -pix_fmt bgr24 -s %WIDTH%x%HEIGHT% -r %FPS% -i - -i %AUDIO_TRACK% -vf %FILTERS% -c:v libx264 -preset ultrafast -tune zerolatency -qp 18 -pix_fmt yuv420p -c:a aac -b:a 128k -shortest %NAME%.mp4";
	public static final String DEFAULT_MUX_FFMPEG_ARGUMENTS = "-y -i %VIDEO% -i %AUDIO_TRACK% -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 192k -shortest %NAME%.mp4";

	public static ValueColors favoriteColors;
	public static ValueColors recentColors;
	public static ValueStringKeys disabledSheets;
	public static ValueTrackStyles trackStyles;
	public static ValueStringKeys disabledMorphFormCategories;
	public static ValueLanguage language;
	public static ValueInt primaryColor;
	public static ValueInt stencilHighlightColor;
	public static ValueBoolean enableTrackpadIncrements;
	public static ValueBoolean enableTrackpadScrolling;
	public static ValueFloat userIntefaceScale;
	public static ValueBoolean pixelArtSmoothing;
	public static ValueInt taskbarSide;
	public static ValueFloat fov;
	public static ValueBoolean colorPickerHsvTab;
	public static ValueBoolean forceQwerty;
	public static ValueBoolean freezeModels;
	public static ValueBoolean listModelPreview;
	public static ValueBoolean morphingFocusSearch;
	public static ValueInt formCellSize;
	public static ValueInt textureCellSize;
	public static ValueString textureSort;
	public static ValueLinkList texturePins;
	public static ValueRecentData recentData;
	public static ValueFloat axesScale;
	public static ValueFloat axesThickness;
	public static ValueBoolean gizmoKeepScreenSize;
	public static ValueFloat gizmoPlaneSize;
	public static ValueInt rotate3dSphereMode;
	public static ValueBoolean hideInactiveHandles;
	/* The gizmo always carries every one of its elements; these say which of them
	 * reach the screen and the cursor. See mchorse.bbs_mod.ui.utils.Gizmo.Element. */
	public static ValueBoolean gizmoShowTranslate;
	public static ValueBoolean gizmoShowScale;
	public static ValueBoolean gizmoShowRotate;
	public static ValueBoolean gizmoShowViewRotate;
	public static ValueBoolean gizmoShowSphere;
	public static ValueFloat snapTranslate;
	public static ValueFloat snapRotate;
	public static ValueFloat snapScale;
	public static ValueInt gizmoHoverTolerance;
	public static ValueFloat gizmoOpacity;
	public static ValueBoolean uniformScale;
	public static ValueBoolean clickSound;
	public static ValueBoolean gizmos;
	public static ValueInt transformSpace;
	public static ValueBoolean poseMirrorEdit;
	public static ValueBoolean poseAlternateInvert;
	public static ValueBoolean poseShowDisabledBones;
	public static ValueOrder translateHotkeyOrder;
	public static ValueOrder scaleHotkeyOrder;
	public static ValueOrder rotateHotkeyOrder;
	public static ValueFloat trackballSensitivity;

	public static ValueBoolean enableCursorRendering;
	public static ValueBoolean enableMouseButtonRendering;
	public static ValueBoolean enableKeystrokeRendering;
	public static ValueInt keystrokeOffset;
	public static ValueInt keystrokeMode;

	public static ValueLink backgroundImage;
	public static ValueInt backgroundColor;

	public static ValueBoolean chromaSkyEnabled;
	public static ValueInt chromaSkyColor;
	public static ValueBoolean chromaSkyTerrain;
	public static ValueFloat chromaSkyBillboard;

	public static ValueInt scrollbarWidth;
	public static ValueFloat scrollingSensitivity;
	public static ValueFloat scrollingSensitivityHorizontal;
	public static ValueBoolean scrollingSmoothness;
	public static ValueBoolean scrollingDisableSmoothnessInEditors;

	public static ValueBoolean multiskinMultiThreaded;

	public static ValueString videoEncoderPath;
	public static ValueBoolean videoEncoderLog;
	public static ValueBoolean worldExportResizeWindow;
	public static ValueInt videoWidth;
	public static ValueInt videoHeight;
	public static ValueInt videoFrameRate;
	public static ValueBoolean videoLimitFrameRate;
	public static ValueString videoExportPath;
	public static ValueString videoExportFilenameFormat;
	public static ValueBoolean videoExportAudio;
	public static ValueBoolean videoExportMinecraftSounds;
	public static ValueBoolean videoMuteAudioWhileRender;
	public static ValueInt videoMotionBlur;
	public static ValueInt videoHeldFrames;
	public static ValueFloat videoDelay;
	public static ValueBoolean videoOpenFolderAfterExport;
	public static ValueBoolean videoPlaySoundAfterExport;
	public static ValueString videoArguments;
	public static ValueString videoArgumentsAudio;
	public static ValueString videoArgumentsMux;

	public static ValueFloat editorCameraSpeed;
	public static ValueFloat editorCameraAngleSpeed;
	public static ValueInt duration;
	public static ValueBoolean editorLoop;
	public static ValueBoolean autoKeyframe;
	public static ValueInt editorJump;
	public static ValueInt editorGuidesColor;
	public static ValueBoolean editorRuleOfThirds;
	public static ValueBoolean editorCenterLines;
	public static ValueBoolean editorCrosshair;
	public static ValueBoolean editorSeconds;
	public static ValueBoolean editorTimelineGrid;
	public static ValueInt editorPeriodicSave;
	public static ValueBoolean editorHorizontalFlight;
	public static ValueBoolean editorOrbitMovementRequiresFlight;
	public static ValueBoolean editorOrbitCenterMarker;
	public static ValueBoolean editorOrbitGizmo;
	public static ValueFloat editorOrbitGizmoScale;
	public static ValueBoolean editorOrbitAxisOrtho;
	public static ValueMotionPath editorMotionPath;
	public static ValueBoolean editorOrbitTeleportOnSwitch;
	public static ValueFloat editorCameraSmoothness;
	public static ValueInt editorCameraMode;
	public static ValueBoolean editorPlayerFollowsCamera;
	public static ValueEditorLayout editorLayoutSettings;
	public static ValueOnionSkin editorOnionSkin;
	public static ValueIKDebug ikDebug;
	public static ValuePhysicsDebug physicsDebug;
	public static ValueBoolean profilerOverlay;
	/** Emergency switch for the per-frame pose caches; invisible, on by default. */
	public static ValueBoolean framePoseCache;
	public static ValueBoolean editorSnapToMarkers;
	/** Snapping to the film's own markers &mdash; unlike {@link #editorSnapToMarkers}, which is the ruler's notches. */
	public static ValueBoolean editorSnapToFilmMarkers;
	public static ValueBoolean editorClipPreview;
	public static ValueBoolean editorRewind;
	public static ValueBoolean editorStopPlaybackOnScrub;
	public static ValueBoolean editorRestartOnSeek;
	public static ValueBoolean editorHorizontalClipEditor;
	public static ValueBoolean editorMinutesBackup;
	public static ValueBoolean editorResizablePanels;
	public static ValueInt editorTrackWidth;
	public static ValueKeyframeStyle keyframeDefaultStyle;
	public static ValueString keyframeDefaultInterpolation;
	public static ValueBoolean keyframePreview;
	public static ValueInt editorPreviewSizeMode;
	public static ValueInt editorPreviewCustomWidth;
	public static ValueInt editorPreviewCustomHeight;
	public static ValueFloat editorPreviewResolutionScale;
	public static ValueBoolean editorClipAutoName;
	public static ValueBoolean editorPreviewIconsAutoHide;
	public static ValueBoolean editorPreviewSelectionHud;
	public static ValueBoolean editorKeepFrameOnExit;

	public static ValueFloat recordingCountdown;
	public static ValueBoolean recordingSwipeDamage;
	public static ValueBoolean recordingOverlays;
	public static ValueInt recordingPoseTransformOverlays;
	public static ValueBoolean recordingCameraPreview;
	public static ValueBoolean recordingTeleport;

	public static ValueBoolean renderAllModelBlocks;
	public static ValueBoolean clickModelBlocks;

	public static ValueString entitySelectorsPropertyWhitelist;

	public static ValueBoolean damageControl;

	public static ValueInt secondaryColor;
	public static ValueFloat overlayBackgroundOpacity;
	public static ValueBoolean interfaceShadows;
	public static ValueBoolean interfaceHighlights;
	public static ValueBoolean interfaceGlow;

	public static ValueBoolean shaderCurvesEnabled;
	public static ValueBoolean translucencyQueue;

	public static ValueBoolean audioWaveformVisibleInPreview;
	public static ValueBoolean audioWaveformVisibleInKeyframes;
	public static ValueInt audioWaveformDensity;
	public static ValueFloat audioWaveformWidth;
	public static ValueInt audioWaveformHeight;
	public static ValueBoolean audioWaveformFilename;
	public static ValueBoolean audioWaveformTime;
	public static ValueBoolean audioWaveformPreviewCombined;

	public static ValueString cdnUrl;
	public static ValueString cdnToken;

	private static final int DEFAULT_PRIMARY_COLOR = 0xff3242;
	private static final float DEFAULT_OVERLAY_BACKGROUND_OPACITY = 0.5F;

	/**
	 * Tonal map of the interface's surfaces, four levels deep: deep sits under
	 * the content (fields, timeline wells), chrome frames everything, base is
	 * the working area, raised floats above it (panels, popups, buttons), and
	 * the divider line sits a step above all of them.
	 *
	 * All five fall out of a single colour — the secondary colour the user
	 * picks — by stepping its lightness in Oklab and carrying its tint through
	 * untouched. Oklab is what makes one colour enough: a step there reads as
	 * the same step in depth whatever the tint, so the ladder stays as legible
	 * in a blue interface as in a grey one, and picking a background is one
	 * decision rather than a pile of them.
	 *
	 * How far apart the levels sit came off a screenshot of Essential's
	 * interface, whose dominant grey and the greys layered over it stand one
	 * step apart — {@link #DEFAULT_SECONDARY_COLOR} reproduces that ramp
	 * exactly (#131313, #181818, #1d1d1d, #222222, divider #2a2a2a), with one
	 * further rung below #131313 for the strips that sit under all of it. The step
	 * is deliberately small: depth should be felt rather than announced, and a
	 * dark interface that stays dark is easier to sit in front of for hours.
	 */
	private static final int DEFAULT_SECONDARY_COLOR = 0x1d1d1d;
	private static final float SURFACE_STEP = 0.022F;
	private static final float DIVIDER_STEP = 0.054F;

	private static final int SURFACE_SUNKEN = 0;
	private static final int SURFACE_DEEP = 1;
	private static final int SURFACE_CHROME = 2;
	private static final int SURFACE_BASE = 3;
	private static final int SURFACE_RAISED = 4;
	private static final int SURFACE_DIVIDER = 5;

	private static final float[] SURFACE_OFFSETS = {-SURFACE_STEP * 3F, -SURFACE_STEP * 2F, -SURFACE_STEP, 0F, SURFACE_STEP, DIVIDER_STEP};

	/**
	 * The lightness past which the surfaces are bright enough that white icons
	 * and text would vanish into them. It is read off the secondary colour
	 * rather than chosen: pick a light one and the interface turns light by
	 * itself, which is why there is no theme switch any more.
	 */
	private static final float LIGHT_SURFACE_LIGHTNESS = 0.5F;

	private static final Oklab SURFACE_OKLAB = new Oklab();
	private static final int[] SURFACES = new int[SURFACE_OFFSETS.length];

	/** The colour {@link #SURFACES} was derived from; -1 is no colour, so the first read builds. */
	private static int surfaceSource = -1;
	private static boolean lightSurfaces;

	public static int primaryColor()
	{
		return primaryColor(Colors.A50);
	}

	public static int primaryColor(int alpha)
	{
		return withAlpha(primaryColor.get(), alpha);
	}

	private static int withAlpha(int color, int alpha)
	{
		return (color & Colors.RGB) | alpha;
	}

	/**
	 * Rebuild the ladder, but only when the secondary colour actually moved —
	 * surfaces are asked for many times a frame, and the conversion is a
	 * handful of cube roots.
	 */
	private static void buildSurfaces()
	{
		int color = secondaryColor == null ? DEFAULT_SECONDARY_COLOR : secondaryColor.get() & Colors.RGB;

		if (color == surfaceSource)
		{
			return;
		}

		SURFACE_OKLAB.set(color);

		for (int i = 0; i < SURFACES.length; i++)
		{
			SURFACES[i] = SURFACE_OKLAB.toRGB(SURFACE_OKLAB.l + SURFACE_OFFSETS[i]);
		}

		lightSurfaces = SURFACE_OKLAB.l > LIGHT_SURFACE_LIGHTNESS;
		surfaceSource = color;
	}

	private static int surface(int level)
	{
		buildSurfaces();

		return SURFACES[level];
	}

	/**
	 * Whether the interface currently sits on light surfaces, in which case
	 * white icons and text have to be flipped to dark to stay readable.
	 */
	public static boolean lightSurfaces()
	{
		buildSurfaces();

		return lightSurfaces;
	}

	public static int chromeSurface()
	{
		return surface(SURFACE_CHROME);
	}

	public static int baseSurface()
	{
		return surface(SURFACE_BASE);
	}

	public static int raisedSurface()
	{
		return surface(SURFACE_RAISED);
	}

	public static int deepSurface()
	{
		return surface(SURFACE_DEEP);
	}

	/**
	 * One rung below {@link #deepSurface()}: the floor of the ladder, for the
	 * strips that have to sit under everything the interface layers on top —
	 * the timeline ruler and the field outside the film, which is not a surface
	 * anything can be put on.
	 */
	public static int sunkenSurface()
	{
		return surface(SURFACE_SUNKEN);
	}

	public static int dividerColor()
	{
		return surface(SURFACE_DIVIDER);
	}

	public static int color(int color, int alpha)
	{
		return withAlpha(color, alpha);
	}

	public static int accentOverlay(int alpha)
	{
		return primaryColor(alpha);
	}

	/**
	 * Render-scoped: the film editor sets this so its inputs stay light on its dark panels.
	 */
	public static boolean lightInputs = false;

	public static int inputSurface()
	{
		return lightInputs ? raisedSurface() : deepSurface();
	}

	public static int panelShadowOpaqueColor()
	{
		return Colors.A25 | primaryColor.get();
	}

	public static int panelShadowTransparentColor()
	{
		return Colors.setA(primaryColor.get(), 0F);
	}

	/**
	 * Dimming behind an overlay panel. Zero opacity leaves whatever is behind
	 * the panel fully visible.
	 */
	public static int overlayBackground()
	{
		float opacity = overlayBackgroundOpacity == null ? DEFAULT_OVERLAY_BACKGROUND_OPACITY : overlayBackgroundOpacity.get();

		return Colors.a(MathUtils.clamp(opacity, 0F, 1F));
	}

	/**
	 * Whether the interface draws its soft glows at all. Every one of them goes
	 * through {@code Batcher2D.dropShadow}, so this is read there rather than
	 * at each caller — the toggle covers panels, context menus, tooltips,
	 * notifications and anything added later without them knowing about it.
	 */
	public static boolean hasInterfaceGlow()
	{
		return interfaceGlow == null || interfaceGlow.get();
	}

	public static int getDefaultDuration()
	{
		return duration == null ? 30 : duration.get();
	}

	public static float getFov()
	{
		return BBSSettings.fov == null ? MathUtils.toRad(50) : MathUtils.toRad(BBSSettings.fov.get());
	}

	/**
	 * How much a world-space overlay has to grow with distance to keep the same size on
	 * screen. Markers and paths always want this - what they mark is a point, and a point
	 * that shrinks into nothing marks nothing.
	 */
	public static float getScreenSizeScale(float distance)
	{
		return getScreenSizeScale(distance, getFov());
	}

	public static float getScreenSizeScale(float distance, float fov)
	{
		float tanFov = (float) Math.tan(fov / 2.0);
		// 0.4663F is roughly tan(50 degrees / 2)
		float scale = (distance / 5F) * (tanFov / 0.4663F);

		return Math.max(scale, 0.0001F);
	}

	/**
	 * The same for the gizmo, which is the one overlay that may turn it off: a gizmo that
	 * shrinks with distance reads as part of the scene rather than as a tool over it, and
	 * some people prefer it that way.
	 */
	public static float getGizmoDistanceScale(float distance, float fov)
	{
		boolean keep = gizmoKeepScreenSize == null || gizmoKeepScreenSize.get();

		return keep ? getScreenSizeScale(distance, fov) : 1F;
	}

	public static boolean isHorizontalClipEditorEffective()
	{
		return editorHorizontalClipEditor.get();
	}

	/**
	 * A fresh copy of the style newly created keyframes are drawn with. It is a copy because the
	 * keyframe owns what it gets: editing one keyframe's style must not reach back into the setting
	 * every other keyframe was born from.
	 */
	public static KeyframeStyle getDefaultKeyframeStyle()
	{
		return keyframeDefaultStyle == null ? new KeyframeStyle() : keyframeDefaultStyle.get().copy();
	}

	/**
	 * The interpolation given to a hand-created keyframe when it has no neighbour to inherit
	 * from (see {@code IUIKeyframeGraph#addKeyframeManually}) - i.e. the replacement for the
	 * hardcoded linear that used to apply in that "empty spot" case. Keyframes that do inherit
	 * from a neighbour keep the neighbour's interpolation, and recorded/baked keyframes never
	 * consult this. Falls back to linear before settings are registered or on an unknown key.
	 */
	public static IInterp getDefaultKeyframeInterpolation()
	{
		if (keyframeDefaultInterpolation == null)
		{
			return Interpolations.LINEAR;
		}

		IInterp interp = Interpolations.MAP.get(keyframeDefaultInterpolation.get());

		return interp == null ? Interpolations.LINEAR : interp;
	}

	/**
	 * Bring a settings file written by an older version onto the current category
	 * layout. Every rule moves a value out of the category it used to live in and
	 * into the one it lives in now; a value that already exists in the new
	 * category wins, so migrating never overwrites a newer setting. The file is
	 * rewritten by {@link mchorse.bbs_mod.settings.SettingsManager} right after,
	 * which is what drops the emptied out legacy categories.
	 */
	public static boolean migrateLegacySettings(MapType root)
	{
		boolean migrated = false;

		/* Colors and timeline looks moved out of the general appearance category */
		migrated |= migrateLegacyCategory(root, "appearance", "personalization", "primary_color", "track_width", "keyframe_default_shape");

		/* The camera editor category got split into the parts it was made of */
		migrated |= migrateLegacyCategory(root, "editor", "camera",
			"speed", "angle_speed", "horizontal_flight", "camera_smoothness", "player_follows_camera",
			"orbit_movement_requires_flight", "orbit_center_marker", "orbit_gizmo", "orbit_gizmo_scale",
			"orbit_axis_ortho", "orbit_teleport_on_switch", "camera_mode");
		migrated |= migrateLegacyCategory(root, "editor", "viewport",
			"guides_color", "rule_of_thirds", "center_lines", "crosshair", "preview_size_mode",
			"preview_custom_width", "preview_custom_height", "preview_resolution_scale", "clip_preview",
			"onion_skin", "motion_path", "ik_debug", "physics_debug");
		migrated |= migrateLegacyCategory(root, "editor", "timeline",
			"duration", "jump", "loop", "seconds", "timeline_grid", "keyframe_default_interpolation",
			"snap_to_markers", "rewind", "horizontal_clip_editor");
		migrated |= migrateLegacyCategory(root, "editor", "workspace",
			"layout", "resizable_panels", "periodic_save", "minutes_backup", "keep_frame_on_exit");
		/* Debug overlays briefly had a category of their own, which had nothing to
		 * show since they are edited from the IK and physics panels */
		migrated |= migrateLegacyCategory(root, "debug", "viewport", "ik_debug", "physics_debug");

		/* The panel glow became a glow toggle for the whole interface */
		migrated |= migrateLegacyValue(root, "personalization", "overlay_gradient_border", "personalization", "interface_glow");

		/* Timeline looks and clip naming joined the categories they belong to */
		migrated |= migrateLegacyCategory(root, "personalization", "timeline", "track_width", "keyframe_default_shape");
		migrated |= migrateLegacyCategory(root, "appearance", "workspace", "clip_auto_name");

		/* Video capture was briefly split three ways, which turned out to be worse
		 * than the one long page it came from */
		migrated |= migrateLegacyCategory(root, "export", "video",
			"export_path", "filename_format", "open_folder_after_export", "play_sound_after_export",
			"world_export_resize_window", "audio", "minecraft_sounds", "mute_audio_while_render");
		migrated |= migrateLegacyCategory(root, "encoder", "video",
			"encoder_path", "log", "arguments", "arguments_audio", "arguments_mux");

		/* The gizmo lost its display modes: every element is always there, and these
		 * two toggles became part of the per-element visibility set */
		migrated |= migrateLegacyValue(root, "transformation", "axes_keep_screen_size", "transformation", "gizmo_keep_screen_size");
		migrated |= migrateLegacyValue(root, "transformation", "rotate_3d_sphere", "transformation", "gizmo_show_sphere");
		migrated |= migrateLegacyFlipped(root, "transformation", "rotate_hide_rings", "transformation", "gizmo_show_rotate");

		/* Single option features share one category now, so their ids say what they switch */
		migrated |= migrateLegacyValue(root, "dc", "enabled", "misc", "damage_control");
		migrated |= migrateLegacyValue(root, "shader_curves", "enabled", "misc", "shader_curves");
		migrated |= migrateLegacyValue(root, "multiskin", "multithreaded", "misc", "multiskin_multithreaded");
		migrated |= migrateLegacyValue(root, "entity_selectors", "whitelist", "misc", "entity_selectors_whitelist");

		return migrated;
	}

	private static boolean migrateLegacyCategory(MapType root, String oldCategory, String newCategory, String... keys)
	{
		boolean migrated = false;

		for (String key : keys)
		{
			migrated |= migrateLegacyValue(root, oldCategory, key, newCategory, key);
		}

		return migrated;
	}

	/**
	 * The same, for a boolean whose meaning was turned around by the rename
	 * ("hide X" becoming "show X"), so the migrated file keeps the look the user had.
	 */
	private static boolean migrateLegacyFlipped(MapType root, String oldCategory, String oldKey, String newCategory, String newKey)
	{
		MapType oldMap = root.getMap(oldCategory);
		MapType newMap = root.getMap(newCategory);

		if (newMap.has(newKey) || !oldMap.has(oldKey))
		{
			return false;
		}

		newMap.putBool(newKey, !oldMap.getBool(oldKey));
		root.put(newCategory, newMap);

		return true;
	}

	private static boolean migrateLegacyValue(MapType root, String oldCategory, String oldKey, String newCategory, String newKey)
	{
		MapType oldMap = root.getMap(oldCategory);
		MapType newMap = root.getMap(newCategory);

		if (newMap.has(newKey) || !oldMap.has(oldKey))
		{
			return false;
		}

		newMap.put(newKey, oldMap.get(oldKey).copy());
		root.put(newCategory, newMap);

		return true;
	}

	public static void register(SettingsBuilder builder)
	{
		HashSet<String> defaultFilters = new HashSet<>();

		defaultFilters.add("item_off_hand");
		defaultFilters.add("item_head");
		defaultFilters.add("item_chest");
		defaultFilters.add("item_legs");
		defaultFilters.add("item_feet");
		defaultFilters.add("vX");
		defaultFilters.add("vY");
		defaultFilters.add("vZ");
		defaultFilters.add("grounded");
		defaultFilters.add("leaning");
		defaultFilters.add("roll");
		defaultFilters.add("stick_rx");
		defaultFilters.add("stick_ry");
		defaultFilters.add("trigger_l");
		defaultFilters.add("trigger_r");
		defaultFilters.add("extra1_x");
		defaultFilters.add("extra1_y");
		defaultFilters.add("extra2_x");
		defaultFilters.add("extra2_y");

		/* Interface */
		builder.category("appearance", Icons.LAYOUT);
		builder.register(language = new ValueLanguage("language"));
		enableTrackpadIncrements = builder.getBoolean("trackpad_increments", false);
		enableTrackpadScrolling = builder.getBoolean("trackpad_scrolling", false);
		userIntefaceScale = builder.getFloat("ui_scale", 2F, 0F, 4F).slider(0.25D);
		pixelArtSmoothing = builder.getBoolean("pixel_art_smoothing", true);
		taskbarSide = builder.getInt("taskbar_side", 0);
		fov = builder.getFloat("fov", 40, 0, 180);
		colorPickerHsvTab = builder.getBoolean("hsv_color_picker", true);
		forceQwerty = builder.getBoolean("force_qwerty", false);
		freezeModels = builder.getBoolean("freeze_models", false);
		listModelPreview = builder.getBoolean("list_model_preview", true);
		morphingFocusSearch = builder.getBoolean("morphing_focus_search", false);
		formCellSize = builder.getInt("form_cell_size", 60, 40, 140).slider();
		textureCellSize = builder.getInt("texture_cell_size", 80, 40, 200).slider();
		textureSort = builder.getString("texture_sort", "name");
		texturePins = new ValueLinkList("texture_pins");
		texturePins.invisible();
		builder.register(texturePins);
		recentData = new ValueRecentData("recent_data");
		recentData.invisible();
		builder.register(recentData);
		/* Kept by the browsers themselves (Ctrl+wheel, the sort menu); nothing to tune in the settings screen */
		formCellSize.invisible();
		textureCellSize.invisible();
		textureSort.invisible();
		/* Which tab the colour picker was left on, written by the picker itself when
		 * the tab is switched - a remembered position, not a setting to sit in a list.
		 * The key stays "hsv_color_picker" so an existing settings file keeps its tab. */
		colorPickerHsvTab.invisible();
		uniformScale = builder.getBoolean("uniform_scale", false);
		clickSound = builder.getBoolean("click_sound", false);
		favoriteColors = new ValueColors("favorite_colors");
		recentColors = new ValueColors("recent_colors").limit(33);
		disabledSheets = new ValueStringKeys("disabled_sheets");
		disabledSheets.set(defaultFilters);
		builder.register(favoriteColors);
		builder.register(recentColors);
		builder.register(disabledSheets);
		trackStyles = new ValueTrackStyles("track_styles");
		builder.register(trackStyles);
		disabledMorphFormCategories = new ValueStringKeys("disabled_morph_form_categories");
		builder.register(disabledMorphFormCategories);

		builder.category("personalization", Icons.COLOR);
		primaryColor = builder.getInt("primary_color", DEFAULT_PRIMARY_COLOR).color();
		secondaryColor = builder.getInt("secondary_color", DEFAULT_SECONDARY_COLOR).color();
		stencilHighlightColor = builder.getInt("stencil_highlight_color", 0x2EFFFFFF).colorAlpha();
		overlayBackgroundOpacity = builder.getFloat("overlay_background_opacity", DEFAULT_OVERLAY_BACKGROUND_OPACITY, 0F, 1F).slider();
		interfaceShadows = builder.getBoolean("interface_shadows", true);
		interfaceHighlights = builder.getBoolean("interface_highlights", false);
		interfaceGlow = builder.getBoolean("interface_glow", false);

		builder.category("scrollbars", Icons.VERTICAL);
		scrollbarWidth = builder.getInt("width", 4, 2, 10).slider();
		scrollingSensitivity = builder.getFloat("sensitivity", 3F, 0F, 10F).slider();
		scrollingSensitivityHorizontal = builder.getFloat("sensitivity_horizontal", 3F, 0F, 10F).slider();
		scrollingSmoothness = builder.getBoolean("smoothness", true);
		scrollingDisableSmoothnessInEditors = builder.getBoolean("disable_smoothness_in_editors", false);

		builder.category("tutorials", Icons.HELP);
		enableCursorRendering = builder.getBoolean("cursor", false);
		enableMouseButtonRendering = builder.getBoolean("mouse_buttons", false);
		enableKeystrokeRendering = builder.getBoolean("keystrokes", false);
		keystrokeOffset = builder.getInt("keystrokes_offset", 10, 0, 20).slider();
		keystrokeMode = builder.getInt("keystrokes_position", 1);

		/* Viewport */
		builder.category("transformation", Icons.SCALE);
		gizmos = builder.getBoolean("gizmos", true);
		axesScale = builder.getFloat("axes_scale", 2F, 0F, 10F).slider();
		axesThickness = builder.getFloat("axes_thickness", 0.35F, 0.25F, 3F).slider();
		gizmoPlaneSize = builder.getFloat("gizmo_plane_size", 1F, 0.25F, 3F).slider();
		gizmoKeepScreenSize = builder.getBoolean("gizmo_keep_screen_size", true);
		gizmoShowTranslate = builder.getBoolean("gizmo_show_translate", true);
		gizmoShowScale = builder.getBoolean("gizmo_show_scale", true);
		gizmoShowRotate = builder.getBoolean("gizmo_show_rotate", true);
		gizmoShowViewRotate = builder.getBoolean("gizmo_show_view_rotate", true);
		gizmoShowSphere = builder.getBoolean("gizmo_show_sphere", true);
		rotate3dSphereMode = builder.getInt("rotate_3d_sphere_mode", 0);
		hideInactiveHandles = builder.getBoolean("hide_inactive_handles", true);
		snapTranslate = builder.getFloat("snap_translate", 1F, 0.001F, 100F);
		snapRotate = builder.getFloat("snap_rotate", 5F, 0.001F, 90F);
		snapScale = builder.getFloat("snap_scale", 0.1F, 0.001F, 10F);
		gizmoHoverTolerance = builder.getInt("gizmo_hover_tolerance", 8, 0, 40).slider();
		gizmoOpacity = builder.getFloat("gizmo_opacity", 1F, 0.05F, 1F).slider();
		/* The frame every transform editor opens in, remembered from the last
		 * session; picked from the gizmo's own space picker, so it has no row here.
		 * The default is PARENT's ordinal - see TransformSpace, whose constants may
		 * only be appended because this persists the ordinal. */
		transformSpace = builder.getInt("transform_space", 3);
		transformSpace.invisible();
		poseMirrorEdit = builder.getBoolean("pose_mirror_edit", false);
		poseMirrorEdit.invisible();
		poseAlternateInvert = builder.getBoolean("pose_alternate_invert", false);
		poseAlternateInvert.invisible();
		poseShowDisabledBones = builder.getBoolean("pose_show_disabled_bones", false);
		translateHotkeyOrder = new ValueOrder("translate_hotkey_order", "screen", "x", "y", "z");
		builder.register(translateHotkeyOrder);
		scaleHotkeyOrder = new ValueOrder("scale_hotkey_order", "all", "x", "y", "z");
		builder.register(scaleHotkeyOrder);
		rotateHotkeyOrder = new ValueOrder("rotate_hotkey_order", "view", "sphere", "x", "y", "z");
		builder.register(rotateHotkeyOrder);
		trackballSensitivity = builder.getFloat("trackball_sensitivity", 1F, 0.05F, 2F).slider();

		builder.category("camera", Icons.CAMERA);
		editorCameraSpeed = builder.getFloat("speed", 1F, 0.1F, 100F);
		editorCameraAngleSpeed = builder.getFloat("angle_speed", 1F, 0.1F, 100F);
		editorHorizontalFlight = builder.getBoolean("horizontal_flight", false);
		editorCameraSmoothness = builder.getFloat("camera_smoothness", 0.1F, 0F, 0.95F).slider();
		editorPlayerFollowsCamera = builder.getBoolean("player_follows_camera", false);
		editorOrbitMovementRequiresFlight = builder.getBoolean("orbit_movement_requires_flight", true);
		editorOrbitCenterMarker = builder.getBoolean("orbit_center_marker", false);
		editorOrbitGizmo = builder.getBoolean("orbit_gizmo", true);
		editorOrbitGizmoScale = builder.getFloat("orbit_gizmo_scale", 1F, 0.5F, 2F).slider();
		editorOrbitAxisOrtho = builder.getBoolean("orbit_axis_ortho", true);
		editorOrbitTeleportOnSwitch = builder.getBoolean("orbit_teleport_on_switch", true);
		editorCameraMode = builder.getInt("camera_mode", 0, 0, 5);
		editorCameraMode.invisible();

		builder.category("viewport", Icons.FRUSTUM);
		editorGuidesColor = builder.getInt("guides_color", 0xcccc0000).colorAlpha();
		editorRuleOfThirds = builder.getBoolean("rule_of_thirds", false);
		editorCenterLines = builder.getBoolean("center_lines", false);
		editorCrosshair = builder.getBoolean("crosshair", false);
		editorPreviewSizeMode = builder.getInt("preview_size_mode", 0, 0, 2);
		editorPreviewCustomWidth = builder.getInt("preview_custom_width", 1280, 2, 16384);
		editorPreviewCustomHeight = builder.getInt("preview_custom_height", 720, 2, 16384);
		editorPreviewResolutionScale = builder.getFloat("preview_resolution_scale", 2F, 1F, 3F).slider();
		editorClipPreview = builder.getBoolean("clip_preview", true);
		editorPreviewIconsAutoHide = builder.getBoolean("preview_icons_auto_hide", false);
		editorPreviewSelectionHud = builder.getBoolean("preview_selection_hud", true);
		builder.register(editorOnionSkin = new ValueOnionSkin("onion_skin"));
		builder.register(editorMotionPath = new ValueMotionPath("motion_path"));
		/* Overlays drawn over the preview which are edited through the gear in the
		 * IK and physics panels - stored here, no row of their own in the settings */
		builder.register(ikDebug = new ValueIKDebug("ik_debug"));
		builder.register(physicsDebug = new ValuePhysicsDebug("physics_debug"));
		profilerOverlay = builder.getBoolean("profiler_overlay", false);
		framePoseCache = builder.getBoolean("frame_pose_cache", true);
		framePoseCache.invisible();

		builder.category("background", Icons.IMAGE);
		backgroundImage = builder.getRL("image", null);
		backgroundColor = builder.getInt("color", 0x7b000000).colorAlpha();

		builder.category("chroma_sky", Icons.GLOBE);
		chromaSkyEnabled = builder.getBoolean("enabled", false);
		chromaSkyColor = builder.getInt("color", Colors.A75).color();
		chromaSkyTerrain = builder.getBoolean("terrain", true);
		chromaSkyBillboard = builder.getFloat("billboard", 0F, 0F, 256F);

		/* Editor */
		builder.category("timeline", Icons.TIME);
		duration = builder.getInt("duration", 30, 1, 1000);
		editorJump = builder.getInt("jump", 5, 1, 1000);
		editorLoop = builder.getBoolean("loop", false);
		autoKeyframe = builder.getBoolean("auto_keyframe", false);
		editorSeconds = builder.getBoolean("seconds", false);
		editorTimelineGrid = builder.getBoolean("timeline_grid", false);
		keyframeDefaultInterpolation = builder.getString("keyframe_default_interpolation", Interpolations.LINEAR.getKey());
		builder.register(keyframeDefaultStyle = new ValueKeyframeStyle("keyframe_default_style"));
		keyframePreview = builder.getBoolean("keyframe_preview", true);
		editorTrackWidth = builder.getInt("track_width", 2, 1, 10).slider();
		editorSnapToMarkers = builder.getBoolean("snap_to_markers", false);
		editorSnapToFilmMarkers = builder.getBoolean("snap_to_film_markers", true);
		editorRewind = builder.getBoolean("rewind", true);
		editorStopPlaybackOnScrub = builder.getBoolean("stop_playback_on_scrub", false);
		editorRestartOnSeek = builder.getBoolean("restart_on_seek", false);
		editorHorizontalClipEditor = builder.getBoolean("horizontal_clip_editor", false);

		builder.category("workspace", Icons.EDITOR);
		builder.register(editorLayoutSettings = new ValueEditorLayout("layout"));
		editorResizablePanels = builder.getBoolean("resizable_panels", true);
		editorPeriodicSave = builder.getInt("periodic_save", 60, 0, 3600);
		editorMinutesBackup = builder.getBoolean("minutes_backup", true);
		editorKeepFrameOnExit = builder.getBoolean("keep_frame_on_exit", false);
		editorClipAutoName = builder.getBoolean("clip_auto_name", true);

		builder.category("recording", Icons.FILM);
		recordingCountdown = builder.getFloat("countdown", 1.5F, 0F, 30F);
		recordingSwipeDamage = builder.getBoolean("swipe_damage", false);
		recordingOverlays = builder.getBoolean("overlays", true);
		recordingPoseTransformOverlays = builder.getInt("pose_transform_overlays", 0, 0, 42);
		recordingCameraPreview = builder.getBoolean("camera_preview", true);
		recordingTeleport = builder.getBoolean("teleport", true);

		/* Output */
		/* Ordered by how often it gets touched: the resolution first, then the
		 * file, the sound and the frames, and the encoder last */
		builder.category("video", Icons.VIDEO_CAMERA);
		videoWidth = builder.getInt("width", 1280, 2, 8096);
		videoHeight = builder.getInt("height", 720, 2, 8096);
		videoFrameRate = builder.getInt("frame_rate", 60, 10, 1000);
		videoLimitFrameRate = builder.getBoolean("limit_frame_rate", false);
		worldExportResizeWindow = builder.getBoolean("world_export_resize_window", false);
		videoExportPath = builder.getString("export_path", "");
		videoExportFilenameFormat = builder.getString("filename_format", "{datetime}");
		videoExportAudio = builder.getBoolean("audio", false);
		videoExportMinecraftSounds = builder.getBoolean("minecraft_sounds", false);
		videoMuteAudioWhileRender = builder.getBoolean("mute_audio_while_render", false);
		videoMotionBlur = builder.getInt("motion_blur", 0, 0, 6);
		videoHeldFrames = builder.getInt("held_frames", 1, 1, 1000);
		videoDelay = builder.getFloat("delay", 0.5F, 0F, 30F);
		videoOpenFolderAfterExport = builder.getBoolean("open_folder_after_export", false);
		videoPlaySoundAfterExport = builder.getBoolean("play_sound_after_export", true);
		videoEncoderPath = builder.getString("encoder_path", "ffmpeg");
		videoEncoderLog = builder.getBoolean("log", true);
		videoArguments = builder.getString("arguments", DEFAULT_FFMPEG_ARGUMENTS);
		videoArgumentsAudio = builder.getString("arguments_audio", DEFAULT_AUDIO_FFMPEG_ARGUMENTS);
		videoArgumentsMux = builder.getString("arguments_mux", DEFAULT_MUX_FFMPEG_ARGUMENTS);

		builder.category("audio", Icons.SOUND);
		audioWaveformVisibleInPreview = builder.getBoolean("waveform_visible_preview", true);
		audioWaveformVisibleInKeyframes = builder.getBoolean("waveform_visible_keyframes", true);
		audioWaveformDensity = builder.getInt("waveform_density", 20, 10, 100).slider();
		audioWaveformWidth = builder.getFloat("waveform_width", 0.8F, 0F, 1F).slider();
		audioWaveformHeight = builder.getInt("waveform_height", 24, 10, 40).slider();
		audioWaveformFilename = builder.getBoolean("waveform_filename", false);
		audioWaveformTime = builder.getBoolean("waveform_time", false);
		audioWaveformPreviewCombined = builder.getBoolean("waveform_preview_combined", false);

		/* The rest */
		builder.category("model_blocks", Icons.BLOCK);
		renderAllModelBlocks = builder.getBoolean("render_all", true);
		clickModelBlocks = builder.getBoolean("click", true);

		builder.category("cdn", Icons.SERVER);
		cdnUrl = builder.getString("url", "");
		cdnToken = builder.getString("token", "");

		/* Features owning a single option each - a category per switch would mean
		 * a row in the settings list per switch, so they share one. */
		builder.category("misc", Icons.MORE);
		damageControl = builder.getBoolean("damage_control", true);
		shaderCurvesEnabled = builder.getBoolean("shader_curves", true);
		translucencyQueue = builder.getBoolean("translucency_queue", false);
		multiskinMultiThreaded = builder.getBoolean("multiskin_multithreaded", true);
		entitySelectorsPropertyWhitelist = builder.getString("entity_selectors_whitelist", "CustomName,Name");
	}
}
