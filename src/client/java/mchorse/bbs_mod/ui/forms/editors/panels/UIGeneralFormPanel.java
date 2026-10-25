package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIKeybind;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class UIGeneralFormPanel extends UIFormPanel
{
    public UIKeybind hotkey;

    public UIToggle visible;
    public UIToggle pickable;
    public UIButton filterTracks;
    public UIToggle boneTracks;
    
    public UITextbox trackName;
    public UITextbox name;
    public UIPropTransform transform;
    public UITrackpad[] pivot = new UITrackpad[3];

    public UIToggle hitbox;
    public UITrackpad hitboxWidth;
    public UITrackpad hitboxHeight;
    public UISliderTrackpad hitboxSneakMultiplier;
    public UISliderTrackpad hitboxEyeHeight;

    public UITrackpad hp;
    public UITrackpad speed;
    public UITrackpad stepHeight;

    public UIGeneralFormPanel(UIForm editor)
    {
        super(editor);

        this.hotkey = new UIKeybind((combo) ->
        {
            this.form.hotkey.set(combo.keys.isEmpty() ? 0 : combo.keys.get(0));
        });
        this.hotkey.single().tooltip(UIKeys.FORMS_EDITORS_GENERAL_HOTKEY);

        this.visible = UIValues.toggle(UIKeys.FORMS_EDITORS_GENERAL_VISIBLE, () -> this.form.visible);
        this.pickable = UIValues.toggle(UIKeys.FORMS_EDITORS_GENERAL_PICKABLE, () -> this.form.pickable);
        this.pickable.tooltip(UIKeys.FORMS_EDITORS_GENERAL_PICKABLE_TOOLTIP);
        this.filterTracks = new UIButton(UIKeys.FORMS_EDITORS_GENERAL_FILTER_TRACKS, (b) -> this.openTrackFilter());
        this.filterTracks.tooltip(UIKeys.FORMS_EDITORS_GENERAL_FILTER_TRACKS_TOOLTIP);
        this.boneTracks = new UIToggle(UIKeys.FORMS_EDITORS_GENERAL_BONE_TRACKS, (b) ->
        {
            if (this.form instanceof ModelForm m) m.boneTracks.set(b.getValue());
        });
        this.boneTracks.tooltip(UIKeys.FORMS_EDITORS_GENERAL_BONE_TRACKS_TOOLTIP);
        UIValues.resettable(this.boneTracks, () -> this.form instanceof ModelForm m ? m.boneTracks : null, () ->
        {
            if (this.form instanceof ModelForm m) this.boneTracks.setValue(m.boneTracks.get());
        });
        this.trackName = UIValues.textbox(120, () -> this.form.trackName);
        this.trackName.tooltip(UIKeys.FORMS_EDITORS_GENERAL_TRACK_NAME_TOOLTIP);
        this.name = UIValues.textbox(120, () -> this.form.name);

        this.transform = new UIPropTransform().callbacks(() -> this.form.transform).barBackground();
        this.transform.enableHotkeys();
        this.transform.valueBinding(() -> this.transform.setTransform(this.form == null ? null : this.form.transform.get()));

        IKey raw = IKey.constant("%s (%s)");
        IKey[] axes = {UIKeys.GENERAL_X, UIKeys.GENERAL_Y, UIKeys.GENERAL_Z};
        int[] colors = {Colors.RED, Colors.GREEN, Colors.BLUE};

        for (int i = 0; i < 3; i++)
        {
            UITrackpad field = new UITrackpad((v) -> this.setPivot()).block().onlyNumbers();

            field.tooltip(raw.format(UIKeys.MODEL_EDITOR_MODEL_PIVOT, axes[i]));
            field.textbox.setColor(colors[i]);
            this.pivot[i] = field;
        }

        /* Drawn like the model editor's cube pivot row, under the transform it belongs to — but
         * only a sign here, the form has no geometry of its own to center it on. */
        UIIcon pivotIcon = new UIIcon(Icons.SPHERE, null);

        pivotIcon.disabledColor = pivotIcon.hoverColor = Colors.WHITE;
        pivotIcon.setEnabled(false);

        UIElement pivotRow = this.transform.addRow(pivotIcon, this.pivot[0], this.pivot[1], this.pivot[2]);

        UIValues.resettable(pivotRow, () -> this.form == null ? null : this.form.pivot, null);
        pivotRow.valueBinding(this::readPivot);

        this.hitbox = UIValues.toggle(UIKeys.CAMERA_PANELS_ENABLED, () -> this.form.hitbox);
        this.hitboxWidth = UIValues.trackpad(() -> this.form.hitboxWidth);
        this.hitboxWidth.limit(0).tooltip(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_WIDTH);
        this.hitboxHeight = UIValues.trackpad(() -> this.form.hitboxHeight);
        this.hitboxHeight.limit(0).tooltip(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_HEIGHT);
        this.hitboxSneakMultiplier = new UISliderTrackpad((v) -> this.form.hitboxSneakMultiplier.set(v.floatValue()));
        this.hitboxSneakMultiplier.limit(0, 1);
        this.hitboxEyeHeight = new UISliderTrackpad((v) -> this.form.hitboxEyeHeight.set(v.floatValue()));
        this.hitboxEyeHeight.limit(0, 1);

        this.hp = UIValues.trackpad(() -> this.form.hp);
        this.hp.limit(1F);
        this.speed = UIValues.trackpad(() -> this.form.speed);
        this.speed.limit(0F);
        this.stepHeight = UIValues.trackpad(() -> this.form.stepHeight);
        this.stepHeight.limit(0F);

        UISection display = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_DISPLAY);

        display.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_DISPLAY, this.name),
            this.hotkey, this.visible, this.pickable
        );

        UISection tracks = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_TRACKS);

        tracks.fields.add(this.filterTracks, this.boneTracks, this.trackName);

        UISection transform = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_TRANSFORM);

        transform.fields.add(this.transform);

        UISection hitbox = new UISection(UIKeys.FORMS_EDITORS_GENERAL_HITBOX);

        hitbox.fields.add(
            this.hitbox,
            UI.row(this.hitboxWidth, this.hitboxHeight),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_SNEAK_MULTIPLIER, this.hitboxSneakMultiplier),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HITBOX_EYE_HEIGHT, this.hitboxEyeHeight)
        );

        UISection movement = new UISection(UIKeys.FORMS_EDITORS_GENERAL_SECTION_MOVEMENT);

        movement.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_HP, this.hp),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_MOVEMENT_SPEED, this.speed.tooltip(UIKeys.FORMS_EDITORS_GENERAL_MOVEMENT_SPEED_TOOLTIP)),
            UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_STEP_HEIGHT, this.stepHeight)
        );

        this.options.add(
            display,
            tracks,
            transform,
            hitbox,
            movement
        );
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        /* Everything built through UIValues reads its own property — only the controls that
         * aren't bound to one are filled here. */
        this.hotkey.setKeyCombo(new KeyCombo(IKey.EMPTY, form.hotkey.get()));

        if (form instanceof ModelForm m)
        {
            this.boneTracks.setValue(m.boneTracks.get());
            this.boneTracks.setVisible(true);
        }
        else
        {
            this.boneTracks.setVisible(false);
        }

        this.hitboxSneakMultiplier.setValue(form.hitboxSneakMultiplier.get());
        this.hitboxEyeHeight.setValue(form.hitboxEyeHeight.get());

        this.options.resize();
    }

    private void setPivot()
    {
        this.form.pivot.set(new Vector3f(
            (float) this.pivot[0].getValue(),
            (float) this.pivot[1].getValue(),
            (float) this.pivot[2].getValue()
        ));
    }

    /** Read on every frame the row is drawn, so an undo or a reset shows at once. */
    private void readPivot()
    {
        if (this.form == null)
        {
            return;
        }

        Vector3f pivot = this.form.pivot.get();

        for (int i = 0; i < 3; i++)
        {
            if (this.pivot[i].getValue() != pivot.get(i))
            {
                this.pivot[i].setValue(pivot.get(i));
            }
        }
    }

    private void openTrackFilter()
    {
        if (this.form == null)
        {
            return;
        }

        Set<String> disabled = this.form.disabledTracks.get();
        Set<String> keys = new LinkedHashSet<>();
        Map<String, Integer> keyToColor = new HashMap<>();
        Map<String, IKey> keyToTitle = new HashMap<>();

        for (TrackDescriptor track : TrackCatalog.of(this.form))
        {
            keys.add(track.filterKey());
            keyToColor.put(track.filterKey(), track.color());
            keyToTitle.put(track.filterKey(), track.title());
        }

        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(disabled, keys, keyToColor, keyToTitle);

        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

        panel.onClose(e -> this.form.disabledTracks.set(disabled));
    }
}