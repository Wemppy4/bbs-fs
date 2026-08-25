package mchorse.bbs_mod;

import java.util.HashSet;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
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
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;

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
	public static ValueInt theme;
	public static ValueFloat fov;
	public static ValueBoolean hsvColorPicker;
	public static ValueBoolean forceQwerty;
	public static ValueBoolean freezeModels;
	public static ValueBoolean listModelPreview;
	public static ValueBoolean morphingFocusSearch;
	public static ValueFloat axesScale;
	public static ValueFloat axesThickness;
	public static ValueBoolean axesKeepScreenSize;
	public static ValueBoolean rotate3dSphere;
	public static ValueInt rotate3dSphereMode;
	public static ValueBoolean rotateHideRings;
	public static ValueBoolean hideInactiveHandles;
	public static ValueFloat snapTranslate;
	public static ValueFloat snapRotate;
	public static ValueFloat snapScale;
	public static ValueInt gizmoHoverTolerance;
	public static ValueFloat gizmoOpacity;
	public static ValueBoolean uniformScale;
	public static ValueBoolean clickSound;
	public static ValueBoolean gizmos;
	public static ValueBoolean defaultLocalTransform;
	public static ValueInt transformSpace;
	public static ValueBoolean transformHotkeys3dRay;
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
	public static ValueBoolean editorSnapToMarkers;
	public static ValueBoolean editorClipPreview;
	public static ValueBoolean editorRewind;
	public static ValueBoolean editorStopPlaybackOnScrub;
	public static ValueBoolean editorRestartOnSeek;
	public static ValueBoolean editorHorizontalClipEditor;
	public static ValueBoolean editorMinutesBackup;
	public static ValueBoolean editorResizablePanels;
	public static ValueInt editorTrackWidth;
	public static ValueInt keyframeDefaultShape;
	public static ValueString keyframeDefaultInterpolation;
	public static ValueBoolean keyframePreview;
	public static ValueInt editorPreviewSizeMode;
	public static ValueInt editorPreviewCustomWidth;
	public static ValueInt editorPreviewCustomHeight;
	public static ValueFloat editorPreviewResolutionScale;
	public static ValueBoolean editorClipAutoName;
	public static ValueBoolean editorPreviewIconsAutoHide;
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

	public static ValueFloat backgroundBrightness;
	public static ValueBoolean interfaceShadows;
	public static ValueBoolean interfaceHighlights;
	public static ValueFloat overlayBackgroundOpacity;
	public static ValueBoolean overlayGradientBorder;

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

	private static final int LIGHT_THEME = 0;
	private static final int DARK_THEME = 1;
	private static final int DEFAULT_THEME = DARK_THEME;
	private static final float DEFAULT_BACKGROUND_BRIGHTNESS = 1F;
	private static final float MIN_BACKGROUND_BRIGHTNESS = 0.5F;
	private static final float MAX_BACKGROUND_BRIGHTNESS = 1.5F;
	private static final float IDENTITY_BRIGHTNESS = 1F;
	private static final float BRIGHTNESS_EPSILON = 0.001F;
	private static final int DEFAULT_PRIMARY_COLOR = 0xff3242;
	private static final float DEFAULT_OVERLAY_BACKGROUND_OPACITY = 0.5F;
	/**
	 * Tonal map of the interface's surfaces, four levels deep: deep sits under
	 * the content (fields, timeline wells), chrome frames everything, base is
	 * the working area, raised floats above it (panels, popups, buttons).
	 *
	 * The levels are a neutral ladder — no tint at all, lightness stepping
	 * evenly by 0.022 in OKLab (the perceptual scale, so the steps read as
	 * equal rather than merely measure as equal). Both themes use the same
	 * step, which makes them mirror images of each other.
	 *
	 * Both where the dark ramp sits and how soft it is come off a screenshot
	 * of Essential's interface. Their dominant grey (#181818, three quarters
	 * of their window) and the greys they layer over it (#1d1d1d cards,
	 * #222222 frame) are these very values, and the step matches the distance
	 * they keep between a card and its background. A small step is the whole
	 * point: depth should be felt rather than announced, and a dark interface
	 * that stays dark is easier to sit in front of for hours.
	 */
	private static final int LIGHT_DEEP_SURFACE = 0xffe4e4e4;
	private static final int DARK_DEEP_SURFACE = 0xff131313;
	private static final int LIGHT_CHROME_SURFACE = 0xffebebeb;
	private static final int DARK_CHROME_SURFACE = 0xff181818;
	private static final int LIGHT_BASE_SURFACE = 0xfff3f3f3;
	private static final int DARK_BASE_SURFACE = 0xff1d1d1d;
	private static final int LIGHT_RAISED_SURFACE = 0xfffafafa;
	private static final int DARK_RAISED_SURFACE = 0xff222222;
	private static final int LIGHT_DIVIDER_COLOR = 0xffd9d9d9;
	private static final int DARK_DIVIDER_COLOR = 0xff2a2a2a;

	public static int primaryColor()
	{
		return primaryColor(Colors.A50);
	}

	public static int primaryColor(int alpha)
	{
		return withAlpha(primaryColor.get(), alpha);
	}

	public static boolean isLightTheme()
	{
		return theme != null && theme.get() == LIGHT_THEME;
	}

	private static int withAlpha(int color, int alpha)
	{
		return (color & Colors.RGB) | alpha;
	}

	private static int getThemeColor(int lightColor, int darkColor)
	{
		return isLightTheme() ? lightColor : darkColor;
	}

	private static float getBackgroundBrightnessFactor()
	{
		return backgroundBrightness == null ? DEFAULT_BACKGROUND_BRIGHTNESS : backgroundBrightness.get();
	}

	private static int applyBackgroundBrightness(int color)
	{
		float brightness = MathUtils.clamp(getBackgroundBrightnessFactor(), MIN_BACKGROUND_BRIGHTNESS, MAX_BACKGROUND_BRIGHTNESS);

		if (Math.abs(brightness - IDENTITY_BRIGHTNESS) < BRIGHTNESS_EPSILON)
		{
			return color;
		}

		int a = color & 0xff000000;
		int r = (color >> 16) & 0xff;
		int g = (color >> 8) & 0xff;
		int b = color & 0xff;

		if (brightness < 1F)
		{
			r = Math.round(r * brightness);
			g = Math.round(g * brightness);
			b = Math.round(b * brightness);
		}
		else
		{
			float factor = brightness - 1F;

			r += Math.round((255 - r) * factor);
			g += Math.round((255 - g) * factor);
			b += Math.round((255 - b) * factor);
		}

		r = MathUtils.clamp(r, 0, 255);
		g = MathUtils.clamp(g, 0, 255);
		b = MathUtils.clamp(b, 0, 255);

		return a | (r << 16) | (g << 8) | b;
	}

	private static int getThemeSurface(int lightColor, int darkColor)
	{
		return applyBackgroundBrightness(getThemeColor(lightColor, darkColor));
	}

	public static int chromeSurface()
	{
		return getThemeSurface(LIGHT_CHROME_SURFACE, DARK_CHROME_SURFACE);
	}

	public static int baseSurface()
	{
		return getThemeSurface(LIGHT_BASE_SURFACE, DARK_BASE_SURFACE);
	}

	public static int raisedSurface()
	{
		return getThemeSurface(LIGHT_RAISED_SURFACE, DARK_RAISED_SURFACE);
	}

	public static int deepSurface()
	{
		return getThemeSurface(LIGHT_DEEP_SURFACE, DARK_DEEP_SURFACE);
	}

	public static int dividerColor()
	{
		return getThemeColor(LIGHT_DIVIDER_COLOR, DARK_DIVIDER_COLOR);
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

	public static boolean hasOverlayGradientBorder()
	{
		return overlayGradientBorder == null || overlayGradientBorder.get();
	}

	public static int getDefaultDuration()
	{
		return duration == null ? 30 : duration.get();
	}

	public static float getFov()
	{
		return BBSSettings.fov == null ? MathUtils.toRad(50) : MathUtils.toRad(BBSSettings.fov.get());
	}

	public static float getAxesDistanceScale(float distance)
	{
		return getAxesDistanceScale(distance, getFov());
	}

	public static float getAxesDistanceScale(float distance, float fov)
	{
		if (axesKeepScreenSize != null && axesKeepScreenSize.get())
		{
			float tanFov = (float) Math.tan(fov / 2.0);
			// 0.4663F is roughly tan(50 degrees / 2)
			float scale = (distance / 5F) * (tanFov / 0.4663F);

			return Math.max(scale, 0.0001F);
		}

		return 1F;
	}

	public static boolean isHorizontalClipEditorEffective()
	{
		return editorHorizontalClipEditor.get();
	}

	/**
	 * Returns the user-configured default shape for newly created keyframes. Falls back to
	 * {@link KeyframeShape#SQUARE} before settings are registered or if the stored ordinal
	 * is out of range (e.g. after the enum shrinks in a future version).
	 */
	public static KeyframeShape getDefaultKeyframeShape()
	{
		if (keyframeDefaultShape == null)
		{
			return KeyframeShape.SQUARE;
		}

		int index = keyframeDefaultShape.get();
		KeyframeShape[] values = KeyframeShape.values();

		return index >= 0 && index < values.length ? values[index] : KeyframeShape.SQUARE;
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
		migrated |= migrateLegacyValue(root, "appearance", "tooltip_style", "personalization", "theme");

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
		fov = builder.getFloat("fov", 40, 0, 180);
		hsvColorPicker = builder.getBoolean("hsv_color_picker", true);
		forceQwerty = builder.getBoolean("force_qwerty", false);
		freezeModels = builder.getBoolean("freeze_models", false);
		listModelPreview = builder.getBoolean("list_model_preview", true);
		morphingFocusSearch = builder.getBoolean("morphing_focus_search", false);
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
		backgroundBrightness = builder.getFloat("background_brightness", DEFAULT_BACKGROUND_BRIGHTNESS, MIN_BACKGROUND_BRIGHTNESS, MAX_BACKGROUND_BRIGHTNESS).slider();
		interfaceShadows = builder.getBoolean("interface_shadows", true);
		interfaceHighlights = builder.getBoolean("interface_highlights", false);
		overlayBackgroundOpacity = builder.getFloat("overlay_background_opacity", DEFAULT_OVERLAY_BACKGROUND_OPACITY, 0F, 1F).slider();
		overlayGradientBorder = builder.getBoolean("overlay_gradient_border", true);
		primaryColor = builder.getInt("primary_color", DEFAULT_PRIMARY_COLOR).color();
		stencilHighlightColor = builder.getInt("stencil_highlight_color", 0x2EFFFFFF).colorAlpha();
		theme = builder.getInt("theme", DEFAULT_THEME);

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
		axesKeepScreenSize = builder.getBoolean("axes_keep_screen_size", true);
		rotate3dSphere = builder.getBoolean("rotate_3d_sphere", true);
		rotate3dSphereMode = builder.getInt("rotate_3d_sphere_mode", 0);
		rotateHideRings = builder.getBoolean("rotate_hide_rings", false);
		hideInactiveHandles = builder.getBoolean("hide_inactive_handles", true);
		snapTranslate = builder.getFloat("snap_translate", 1F, 0.001F, 100F);
		snapRotate = builder.getFloat("snap_rotate", 5F, 0.001F, 90F);
		snapScale = builder.getFloat("snap_scale", 0.1F, 0.001F, 10F);
		gizmoHoverTolerance = builder.getInt("gizmo_hover_tolerance", 8, 0, 40).slider();
		gizmoOpacity = builder.getFloat("gizmo_opacity", 1F, 0.05F, 1F).slider();
		defaultLocalTransform = builder.getBoolean("default_local", false);
		transformSpace = builder.getInt("transform_space", defaultLocalTransform.get() ? 0 : 3);
		transformSpace.invisible();
		transformHotkeys3dRay = builder.getBoolean("hotkeys_3d_ray", true);
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
		builder.register(editorOnionSkin = new ValueOnionSkin("onion_skin"));
		builder.register(editorMotionPath = new ValueMotionPath("motion_path"));
		/* Overlays drawn over the preview which are edited through the gear in the
		 * IK and physics panels - stored here, no row of their own in the settings */
		builder.register(ikDebug = new ValueIKDebug("ik_debug"));
		builder.register(physicsDebug = new ValuePhysicsDebug("physics_debug"));

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
		editorSeconds = builder.getBoolean("seconds", false);
		editorTimelineGrid = builder.getBoolean("timeline_grid", false);
		keyframeDefaultInterpolation = builder.getString("keyframe_default_interpolation", Interpolations.LINEAR.getKey());
		keyframeDefaultShape = builder.getInt("keyframe_default_shape", 0, 0, KeyframeShape.values().length - 1);
		keyframePreview = builder.getBoolean("keyframe_preview", true);
		editorTrackWidth = builder.getInt("track_width", 2, 1, 10).slider();
		editorSnapToMarkers = builder.getBoolean("snap_to_markers", false);
		editorRewind = builder.getBoolean("rewind", true);
		editorStopPlaybackOnScrub = builder.getBoolean("stop_playback_on_scrub", false);
		editorRestartOnSeek = builder.getBoolean("restart_on_seek", false);
		editorHorizontalClipEditor = builder.getBoolean("horizontal_clip_editor", true);

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
