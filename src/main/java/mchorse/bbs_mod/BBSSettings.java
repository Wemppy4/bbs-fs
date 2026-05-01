package mchorse.bbs_mod;

import java.util.HashSet;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.settings.values.ui.ValueLanguage;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.settings.values.ui.ValueVideoSettings;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;

public class BBSSettings {

	public static ValueColors favoriteColors;
	public static ValueColors recentColors;
	public static ValueStringKeys disabledSheets;
	public static ValueStringKeys disabledMorphFormCategories;
	public static ValueLanguage language;
	public static ValueInt primaryColor;
	public static ValueBoolean enableTrackpadIncrements;
	public static ValueBoolean enableTrackpadScrolling;
	public static ValueInt userIntefaceScale;
	public static ValueInt theme;
	public static ValueFloat fov;
	public static ValueBoolean hsvColorPicker;
	public static ValueBoolean forceQwerty;
	public static ValueBoolean freezeModels;
	public static ValueBoolean morphingFocusSearch;
	public static ValueFloat axesScale;
	public static ValueFloat axesThickness;
	public static ValueBoolean axesKeepScreenSize;
	public static ValueBoolean rotate3dSphere;
	public static ValueInt rotate3dSphereColor;
	public static ValueBoolean rotateHideRings;
	public static ValueBoolean uniformScale;
	public static ValueBoolean clickSound;
	public static ValueBoolean gizmos;
	public static ValueBoolean transformLocalDefault;
	public static ValueBoolean transformHotkeys3dRay;

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

	public static ValueInt scrollbarShadow;
	public static ValueInt scrollbarWidth;
	public static ValueFloat scrollingSensitivity;
	public static ValueFloat scrollingSensitivityHorizontal;
	public static ValueBoolean scrollingSmoothness;
	public static ValueBoolean scrollingDisableSmoothnessInEditors;

	public static ValueBoolean multiskinMultiThreaded;

	public static ValueString videoEncoderPath;
	public static ValueBoolean videoEncoderLog;
	public static ValueBoolean worldExportResizeWindow;
	public static ValueVideoSettings videoSettings;

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
	public static ValueInt editorPeriodicSave;
	public static ValueBoolean editorHorizontalFlight;
	public static ValueBoolean editorOrbitMovementRequiresFlight;
	public static ValueBoolean editorOrbitCenterMarker;
	public static ValueBoolean editorPlayerFollowsCamera;
	public static ValueEditorLayout editorLayoutSettings;
	public static ValueOnionSkin editorOnionSkin;
	public static ValueBoolean editorSnapToMarkers;
	public static ValueBoolean editorClipPreview;
	public static ValueBoolean editorRewind;
	public static ValueBoolean editorHorizontalClipEditor;
	public static ValueBoolean editorMinutesBackup;
	public static ValueInt editorTrackWidth;
	public static ValueInt keyframeDefaultShape;
	public static ValueInt editorPreviewSizeMode;
	public static ValueInt editorPreviewCustomWidth;
	public static ValueInt editorPreviewCustomHeight;
	public static ValueFloat editorPreviewResolutionScale;
	public static ValueBoolean editorClipAutoName;

	public static ValueFloat recordingCountdown;
	public static ValueBoolean recordingSwipeDamage;
	public static ValueBoolean editorReplayTabs;
	public static ValueBoolean recordingOverlays;
	public static ValueInt recordingPoseTransformOverlays;
	public static ValueBoolean recordingCameraPreview;

	public static ValueBoolean renderAllModelBlocks;
	public static ValueBoolean clickModelBlocks;

	public static ValueString entitySelectorsPropertyWhitelist;

	public static ValueBoolean damageControl;

	public static ValueBoolean coloredBackground;
	public static ValueFloat backgroundBrightness;

	public static ValueBoolean shaderCurvesEnabled;

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
	private static final int DEFAULT_PRIMARY_COLOR = 0xffff1493;
	private static final int LIGHT_CHROME_SURFACE = 0xffe6e9ef;
	private static final int DARK_CHROME_SURFACE = 0xff111316;
	private static final int LIGHT_BASE_SURFACE = 0xfff1f4f8;
	private static final int DARK_BASE_SURFACE = 0xff171a1f;
	private static final int LIGHT_RAISED_SURFACE = 0xfff8fafd;
	private static final int DARK_RAISED_SURFACE = 0xff1d2127;
	private static final int LIGHT_DEEP_SURFACE = 0xffdee4ed;
	private static final int DARK_DEEP_SURFACE = 0xff0f1217;
	private static final int LIGHT_DIVIDER_COLOR = 0xffc2cbd8;
	private static final int DARK_DIVIDER_COLOR = 0xff30353d;

	public static int primaryColor() {
		return primaryColor(Colors.A50);
	}

	public static int primaryColor(int alpha) {
		return primaryColor.get() | alpha;
	}

	public static boolean hasColoredBackground() {
		return coloredBackground == null || coloredBackground.get();
	}

	public static boolean isLightTheme() {
		return theme != null && theme.get() == LIGHT_THEME;
	}

	private static int withAlpha(int color, int alpha) {
		return (color & Colors.RGB) | alpha;
	}

	private static int getThemeColor(int lightColor, int darkColor) {
		return isLightTheme() ? lightColor : darkColor;
	}

	private static float getBackgroundBrightnessFactor() {
		return backgroundBrightness == null ? DEFAULT_BACKGROUND_BRIGHTNESS : backgroundBrightness.get();
	}

	private static int applyBackgroundBrightness(int color) {
		float brightness = MathUtils.clamp(getBackgroundBrightnessFactor(), MIN_BACKGROUND_BRIGHTNESS, MAX_BACKGROUND_BRIGHTNESS);

		if (Math.abs(brightness - IDENTITY_BRIGHTNESS) < BRIGHTNESS_EPSILON) {
			return color;
		}

		int a = color & 0xff000000;
		int r = (color >> 16) & 0xff;
		int g = (color >> 8) & 0xff;
		int b = color & 0xff;

		if (brightness < 1F) {
			r = Math.round(r * brightness);
			g = Math.round(g * brightness);
			b = Math.round(b * brightness);
		}
		else {
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

	private static int getThemeSurface(int lightColor, int darkColor) {
		return applyBackgroundBrightness(getThemeColor(lightColor, darkColor));
	}

	public static int chromeSurface() {
		return getThemeSurface(LIGHT_CHROME_SURFACE, DARK_CHROME_SURFACE);
	}

	public static int baseSurface() {
		return getThemeSurface(LIGHT_BASE_SURFACE, DARK_BASE_SURFACE);
	}

	public static int raisedSurface() {
		return getThemeSurface(LIGHT_RAISED_SURFACE, DARK_RAISED_SURFACE);
	}

	public static int deepSurface() {
		return getThemeSurface(LIGHT_DEEP_SURFACE, DARK_DEEP_SURFACE);
	}

	public static int dividerColor() {
		return getThemeColor(LIGHT_DIVIDER_COLOR, DARK_DIVIDER_COLOR);
	}

	public static int color(int color, int alpha) {
		return withAlpha(color, alpha);
	}

	public static int backgroundTint(int alpha) {
		return hasColoredBackground() ? primaryColor(alpha) : 0;
	}

	public static int accentOverlay(int alpha) {
		return primaryColor(alpha);
	}

	public static int inputSurface() {
		return deepSurface();
	}

	public static int inputSurfaceTint() {
		return backgroundTint(Colors.A6);
	}

	public static int inputBorderColor(boolean focused) {
		return focused ? primaryColor.get() : dividerColor();
	}

	public static int panelShadowOpaqueColor() {
		return Colors.A25 | primaryColor.get();
	}

	public static int panelShadowTransparentColor() {
		return Colors.setA(primaryColor.get(), 0F);
	}

	public static int getDefaultDuration() {
		return duration == null ? 30 : duration.get();
	}

	public static float getFov() {
		return BBSSettings.fov == null ? MathUtils.toRad(50) : MathUtils.toRad(BBSSettings.fov.get());
	}

	public static float getAxesDistanceScale(float distance) {
		return getAxesDistanceScale(distance, getFov());
	}

	public static float getAxesDistanceScale(float distance, float fov) {
		if (axesKeepScreenSize != null && axesKeepScreenSize.get()) {
			float tanFov = (float) Math.tan(fov / 2.0);
			// 0.4663F is roughly tan(50 degrees / 2)
			float scale = (distance / 5F) * (tanFov / 0.4663F);

			return Math.max(scale, 0.0001F);
		}

		return 1F;
	}

	public static boolean isHorizontalClipEditorEffective() {
		return editorHorizontalClipEditor.get();
	}

	/**
	 * Returns the user-configured default shape for newly created keyframes. Falls back to
	 * {@link KeyframeShape#SQUARE} before settings are registered or if the stored ordinal
	 * is out of range (e.g. after the enum shrinks in a future version).
	 */
	public static KeyframeShape getDefaultKeyframeShape() {
		if (keyframeDefaultShape == null) {
			return KeyframeShape.SQUARE;
		}

		int index = keyframeDefaultShape.get();
		KeyframeShape[] values = KeyframeShape.values();

		return index >= 0 && index < values.length ? values[index] : KeyframeShape.SQUARE;
	}

	public static boolean migrateLegacySettings(MapType root) {
		MapType appearance = root.getMap("appearance");
		MapType personalization = root.getMap("personalization");
		boolean migrated = false;

		migrated |= migrateLegacyValue(appearance, personalization, "primary_color");
		migrated |= migrateLegacyValue(appearance, personalization, "tooltip_style", "theme");
		migrated |= migrateLegacyValue(appearance, personalization, "track_width");
		migrated |= migrateLegacyValue(appearance, personalization, "keyframe_default_shape");

		if (migrated) {
			root.put("personalization", personalization);
		}

		return migrated;
	}

	private static boolean migrateLegacyValue(MapType oldCategory, MapType newCategory, String key) {
		return migrateLegacyValue(oldCategory, newCategory, key, key);
	}

	private static boolean migrateLegacyValue(MapType oldCategory, MapType newCategory, String oldKey, String newKey) {
		if (newCategory.has(newKey) || !oldCategory.has(oldKey)) {
			return false;
		}

		newCategory.put(newKey, oldCategory.get(oldKey).copy());

		return true;
	}

	public static void register(SettingsBuilder builder) {
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

		builder.category("appearance");
		builder.register(language = new ValueLanguage("language"));
		enableTrackpadIncrements = builder.getBoolean("trackpad_increments", true);
		enableTrackpadScrolling = builder.getBoolean("trackpad_scrolling", true);
		userIntefaceScale = builder.getInt("ui_scale", 2, 0, 4);
		fov = builder.getFloat("fov", 40, 0, 180);
		hsvColorPicker = builder.getBoolean("hsv_color_picker", true);
		forceQwerty = builder.getBoolean("force_qwerty", false);
		freezeModels = builder.getBoolean("freeze_models", false);
		morphingFocusSearch = builder.getBoolean("morphing_focus_search", false);
		uniformScale = builder.getBoolean("uniform_scale", false);
		clickSound = builder.getBoolean("click_sound", false);
		favoriteColors = new ValueColors("favorite_colors");
		recentColors = new ValueColors("recent_colors");
		disabledSheets = new ValueStringKeys("disabled_sheets");
		disabledSheets.set(defaultFilters);
		builder.register(favoriteColors);
		builder.register(recentColors);
		builder.register(disabledSheets);
		disabledMorphFormCategories = new ValueStringKeys("disabled_morph_form_categories");
		builder.register(disabledMorphFormCategories);
		editorClipAutoName = builder.getBoolean("clip_auto_name", true);

		builder.category("personalization");
		coloredBackground = builder.getBoolean("colored_background", false);
		backgroundBrightness = builder.getFloat("background_brightness", DEFAULT_BACKGROUND_BRIGHTNESS, MIN_BACKGROUND_BRIGHTNESS, MAX_BACKGROUND_BRIGHTNESS);
		primaryColor = builder.getInt("primary_color", DEFAULT_PRIMARY_COLOR).color();
		theme = builder.getInt("theme", DEFAULT_THEME);
		editorTrackWidth = builder.getInt("track_width", 2, 1, 10);
		keyframeDefaultShape = builder.getInt("keyframe_default_shape", 0, 0, KeyframeShape.values().length - 1);

		builder.category("transformation");
		gizmos = builder.getBoolean("gizmos", true);
		axesScale = builder.getFloat("axes_scale", 3F, 0F, 10F);
		axesThickness = builder.getFloat("axes_thickness", 1F, 0.25F, 3F);
		axesKeepScreenSize = builder.getBoolean("axes_keep_screen_size", true);
		rotate3dSphere = builder.getBoolean("rotate_3d_sphere", true);
		rotate3dSphereColor = builder.getInt("rotate_3d_sphere_color", Colors.setA(Colors.WHITE, 0.15F)).colorAlpha();
		rotateHideRings = builder.getBoolean("rotate_hide_rings", false);
		transformLocalDefault = builder.getBoolean("transform_local_default", false);
		transformHotkeys3dRay = builder.getBoolean("hotkeys_3d_ray", true);

		builder.category("tutorials");
		enableCursorRendering = builder.getBoolean("cursor", false);
		enableMouseButtonRendering = builder.getBoolean("mouse_buttons", false);
		enableKeystrokeRendering = builder.getBoolean("keystrokes", false);
		keystrokeOffset = builder.getInt("keystrokes_offset", 10, 0, 20);
		keystrokeMode = builder.getInt("keystrokes_position", 1);

		builder.category("background");
		backgroundImage = builder.getRL("image", Link.assets("textures/banners/bg.png"));
		backgroundColor = builder.getInt("color", Colors.WHITE).colorAlpha();

		builder.category("chroma_sky");
		chromaSkyEnabled = builder.getBoolean("enabled", false);
		chromaSkyColor = builder.getInt("color", Colors.A75).color();
		chromaSkyTerrain = builder.getBoolean("terrain", true);
		chromaSkyBillboard = builder.getFloat("billboard", 0F, 0F, 256F);

		builder.category("scrollbars");
		scrollbarShadow = builder.getInt("shadow", Colors.A50).colorAlpha();
		scrollbarWidth = builder.getInt("width", 4, 2, 10);
		scrollingSensitivity = builder.getFloat("sensitivity", 1F, 0F, 10F);
		scrollingSensitivityHorizontal = builder.getFloat("sensitivity_horizontal", 1F, 0F, 10F);
		scrollingSmoothness = builder.getBoolean("smoothness", true);
		scrollingDisableSmoothnessInEditors = builder.getBoolean("disable_smoothness_in_editors", false);

		builder.category("multiskin");
		multiskinMultiThreaded = builder.getBoolean("multithreaded", true);

		builder.category("video");
		videoEncoderPath = builder.getString("encoder_path", "ffmpeg");
		videoEncoderLog = builder.getBoolean("log", true);
		worldExportResizeWindow = builder.getBoolean("world_export_resize_window", false);
		builder.register(videoSettings = new ValueVideoSettings("settings"));

		/* Camera editor */
		builder.category("editor");
		editorCameraSpeed = builder.getFloat("speed", 1F, 0.1F, 100F);
		editorCameraAngleSpeed = builder.getFloat("angle_speed", 1F, 0.1F, 100F);
		duration = builder.getInt("duration", 30, 1, 1000);
		editorJump = builder.getInt("jump", 5, 1, 1000);
		editorLoop = builder.getBoolean("loop", false);
		editorGuidesColor = builder.getInt("guides_color", 0xcccc0000).colorAlpha();
		editorRuleOfThirds = builder.getBoolean("rule_of_thirds", false);
		editorCenterLines = builder.getBoolean("center_lines", false);
		editorCrosshair = builder.getBoolean("crosshair", false);
		editorSeconds = builder.getBoolean("seconds", false);
		editorPeriodicSave = builder.getInt("periodic_save", 60, 0, 3600);
		editorHorizontalFlight = builder.getBoolean("horizontal_flight", false);
		editorOrbitMovementRequiresFlight = builder.getBoolean("orbit_movement_requires_flight", true);
		editorOrbitCenterMarker = builder.getBoolean("orbit_center_marker", false);
		editorPlayerFollowsCamera = builder.getBoolean("player_follows_camera", false);
		builder.register(editorLayoutSettings = new ValueEditorLayout("layout"));
		builder.register(editorOnionSkin = new ValueOnionSkin("onion_skin"));
		editorSnapToMarkers = builder.getBoolean("snap_to_markers", false);
		editorClipPreview = builder.getBoolean("clip_preview", true);
		editorRewind = builder.getBoolean("rewind", true);
		editorHorizontalClipEditor = builder.getBoolean("horizontal_clip_editor", true);
		editorMinutesBackup = builder.getBoolean("minutes_backup", true);
		editorPreviewSizeMode = builder.getInt("preview_size_mode", 0, 0, 2);
		editorPreviewCustomWidth = builder.getInt("preview_custom_width", 1280, 2, 16384);
		editorPreviewCustomHeight = builder.getInt("preview_custom_height", 720, 2, 16384);
		editorPreviewResolutionScale = builder.getFloat("preview_resolution_scale", 2F, 1F, 3F);

		builder.category("recording");
		recordingCountdown = builder.getFloat("countdown", 1.5F, 0F, 30F);
		recordingSwipeDamage = builder.getBoolean("swipe_damage", false);
		recordingOverlays = builder.getBoolean("overlays", true);
		editorReplayTabs = builder.getBoolean("replay_tabs", true);
		recordingPoseTransformOverlays = builder.getInt("pose_transform_overlays", 0, 0, 42);
		recordingCameraPreview = builder.getBoolean("camera_preview", true);

		builder.category("model_blocks");
		renderAllModelBlocks = builder.getBoolean("render_all", true);
		clickModelBlocks = builder.getBoolean("click", true);

		builder.category("entity_selectors");
		entitySelectorsPropertyWhitelist = builder.getString("whitelist", "CustomName,Name");

		builder.category("dc");
		damageControl = builder.getBoolean("enabled", true);

		builder.category("shader_curves");
		shaderCurvesEnabled = builder.getBoolean("enabled", true);

		builder.category("audio");
		audioWaveformVisibleInPreview = builder.getBoolean("waveform_visible_preview", true);
		audioWaveformVisibleInKeyframes = builder.getBoolean("waveform_visible_keyframes", true);
		audioWaveformDensity = builder.getInt("waveform_density", 20, 10, 100);
		audioWaveformWidth = builder.getFloat("waveform_width", 0.8F, 0F, 1F);
		audioWaveformHeight = builder.getInt("waveform_height", 24, 10, 40);
		audioWaveformFilename = builder.getBoolean("waveform_filename", false);
		audioWaveformTime = builder.getBoolean("waveform_time", false);
		audioWaveformPreviewCombined = builder.getBoolean("waveform_preview_combined", false);

		builder.category("cdn");
		cdnUrl = builder.getString("url", "");
		cdnToken = builder.getString("token", "");
	}
}
