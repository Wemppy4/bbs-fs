package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UILabelListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Label;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UICurveClip extends UIClip<CurveClip>
{
    public UIKeyframeEditor keyframes;
    public UIButton edit;

    public UICurveClip(CurveClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    public static void offerCurveKeys(UIContext context, List<String> existing, Consumer<String> callback)
    {
        List<Label<String>> list = new ArrayList<>();
        String language = BBSModClient.getLanguageKey();
        Map<String, String> languageMap = BBSRendering.getShadersLanguageMap(language);

        for (ShaderCurves.ShaderVariable value : ShaderCurves.variableMap.values())
        {
            if (existing.contains(value.name))
            {
                continue;
            }

            String key = value.name;
            String newKey = languageMap.get("option." + key);

            if (newKey != null)
            {
                key = newKey + " (" + key + ")";
            }

            list.add(new Label<>(IKey.constant(key), CurveClip.SHADER_CURVES_PREFIX + value.name));
        }

        if (!existing.contains(ShaderCurves.BRIGHTNESS)) list.add(new Label<>(UIKeys.CAMERA_PANELS_CURVES_BRIGHTNESS, ShaderCurves.BRIGHTNESS));
        if (!existing.contains(ShaderCurves.SUN_ROTATION)) list.add(new Label<>(UIKeys.CAMERA_PANELS_CURVES_SUN_ROTATION, ShaderCurves.SUN_ROTATION));
        if (!existing.contains(ShaderCurves.WEATHER)) list.add(new Label<>(UIKeys.CAMERA_PANELS_CURVES_WEATHER, ShaderCurves.WEATHER));
        if (!existing.contains(CurveClip.CHROMA_SKY_COLOR)) list.add(new Label<>(UIKeys.CAMERA_PANELS_CURVES_CHROMA_SKY_COLOR, CurveClip.CHROMA_SKY_COLOR));

        UILabelListOverlayPanel panel = new UILabelListOverlayPanel(UIKeys.CAMERA_PANELS_PICK_KEY, list, callback);

        panel.strings.list.sort();

        UIOverlay.addOverlay(context, panel, 0.9F, 0.5F);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.rulerRenderer((context) ->
        {
            UIReplaysEditor.renderRuler(context, this.keyframes.view, (UIClipsPanel) this.editor, (Clips) this.clip.getParent(), this.clip.tick.get());
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("curve_keyframes");

        this.keyframes.view.context((menu) ->
        {
            menu.action(Icons.ADD, UIKeys.CAMERA_PANELS_CURVE_ADD, () ->
            {
                List<String> existing = new ArrayList<>();

                for (KeyframeChannel<?> channel : this.clip.channels.getAllKeyframeChannels())
                {
                    existing.add(channel.getId());
                }

                offerCurveKeys(this.getContext(), existing, (s) ->
                {
                    if (CurveClip.isColorChannelId(s))
                    {
                        this.clip.channels.addChannel(s, KeyframeFactories.COLOR);
                    }
                    else
                    {
                        this.clip.channels.addChannel(s, KeyframeFactories.DOUBLE);
                    }

                    this.fillData();
                });
            }).order(-3);

            UIKeyframeSheet sheet = this.keyframes.view.getDopeSheet().getSheet(this.getContext().mouseY);

            if (sheet != null)
            {
                menu.action(Icons.REMOVE, UIKeys.CAMERA_PANELS_CURVE_REMOVE, Colors.RED, () ->
                {
                    this.clip.channels.removeChannel(sheet.channel);
                    this.fillData();
                });
            }
        });

        this.edit = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    private void addKeyframeSheet(KeyframeChannel<?> channel)
    {
        int sheetColor = channel.getId().hashCode() & Colors.RGB;

        this.keyframes.view.addSheet(new UIKeyframeSheet(channel.getId(), IKey.constant(channel.getId()), sheetColor, false, channel, null));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.C_CLIP.get("bbs:curve"), this.edit).marginTop(UIConstants.SECTION_GAP));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.keyframes.view.removeAllSheets();

        for (KeyframeChannel<?> channel : this.clip.channels.getAllKeyframeChannels())
        {
            this.addKeyframeSheet(channel);
        }
    }

    @Override
    public void applyUndoData(MapType data)
    {
        if (data.getString("embed").equals("curve"))
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }

        super.applyUndoData(data);
    }

    @Override
    public void collectUndoData(MapType data)
    {
        if (this.keyframes.hasParent())
        {
            data.putString("embed", "curve");
        }

        super.collectUndoData(data);
    }
}
