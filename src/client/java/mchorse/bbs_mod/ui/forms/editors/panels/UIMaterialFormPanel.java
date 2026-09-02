package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The "Material" tab: the form's appearance — tint, color overlay, glow, render layer — plus,
 * for models with a real material set, the same properties per material and the PBR sliders.
 *
 * <p>The selector at the top picks the level: "Whole form" edits the form's own properties
 * (which every renderer applies), a material edits its {@link FormMaterial} (layered on top by
 * the model renderer per draw). Bones are NOT here by design — the pose editor owns them, with
 * their selection, gizmo and pose-track channels.</p>
 */
public class UIMaterialFormPanel extends UIFormPanel
{
    public UIButton materialSelector;

    public UIColor color;
    public UIColor overlay;
    public UISliderTrackpad lighting;

    public UIButton pickTexture;

    public UICirculate layer;
    public UICirculate culling;
    public UIToggle shaderShadow;

    public UISliderTrackpad smoothness;
    public UISliderTrackpad metallic;
    public UISliderTrackpad sss;
    public UISliderTrackpad pixelEmission;
    public UISliderTrackpad relief;

    private UISection colorSection;
    private UISection textureSection;
    private UISection renderSection;
    private UISection pbrSection;

    private UIElement colorRow;
    private UIElement overlayRow;
    private UIElement lightingRow;
    private UIElement layerRow;
    private UIElement cullingRow;

    /** Selected material name; null = the whole-form level. */
    private String material;

    public UIMaterialFormPanel(UIForm editor)
    {
        super(editor);

        this.materialSelector = new UIButton(UIKeys.FORMS_EDITORS_MATERIAL_WHOLE_FORM, (b) -> this.openMaterialSelector());

        this.color = new UIColor((c) -> this.applyColor(new Color().set(c))).withAlpha();
        this.overlay = new UIColor((c) -> this.applyOverlay(new Color().set(c))).withAlpha();
        this.overlay.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_OVERLAY_TOOLTIP);
        this.lighting = new UISliderTrackpad((v) -> this.applyLighting(v.floatValue()));
        this.lighting.limit(0D, 1D);
        this.lighting.tooltip(UIKeys.FORMS_EDITORS_GENERAL_LIGHTING_TOOLTIP);

        this.pickTexture = new UIButton(UIKeys.FORMS_EDITOR_MODEL_PICK_TEXTURE, (b) -> this.openTexturePicker());

        this.layer = new UICirculate((b) -> this.form.renderLayer.set(b.getValue()));
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_AUTO);
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_SOLID);
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_CUTOUT);
        this.layer.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_TRANSLUCENT);
        this.layer.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_LAYER_TOOLTIP);
        UIValues.resettable(this.layer, () -> this.form.renderLayer, () -> this.layer.setValue(this.form.renderLayer.get()));

        this.culling = new UICirculate((b) -> this.materialValue().culling.set(b.getValue()));
        this.culling.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_CULLING_MODEL);
        this.culling.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_CULLING_ON);
        this.culling.addLabel(UIKeys.FORMS_EDITORS_MATERIAL_CULLING_OFF);

        this.shaderShadow = UIValues.toggle(UIKeys.FORMS_EDITORS_GENERAL_SHADER_SHADOW, () -> this.form.shaderShadow);

        this.smoothness = this.pbrSlider((v) -> this.materialValue().smoothness.set(v));
        this.metallic = this.pbrSlider((v) -> this.materialValue().metallic.set(v));
        this.sss = this.pbrSlider((v) -> this.materialValue().sss.set(v));
        this.pixelEmission = this.pbrSlider((v) -> this.materialValue().pixelEmission.set(v));
        this.relief = this.pbrSlider((v) -> this.materialValue().relief.set(v));

        this.colorSection = this.section(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_COLOR, "material.color", true);
        this.colorRow = UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_COLOR, this.color);
        this.overlayRow = UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_OVERLAY, this.overlay);
        this.lightingRow = UI.labelRow(UIKeys.FORMS_EDITORS_GENERAL_LIGHTING, this.lighting);

        this.textureSection = this.section(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_TEXTURE, "material.texture", true);
        this.textureSection.fields.add(this.pickTexture);

        this.renderSection = this.section(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_RENDER, "material.render", false);
        this.layerRow = UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_LAYER, this.layer);
        this.cullingRow = UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_CULLING, this.culling);

        this.pbrSection = this.section(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_PBR, "material.pbr", false);
        this.pbrSection.tooltip(UIKeys.FORMS_EDITORS_MATERIAL_SECTION_PBR_TOOLTIP);
        this.pbrSection.fields.add(
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_SMOOTHNESS, this.smoothness),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_METALLIC, this.metallic),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_SSS, this.sss),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_PIXEL_EMISSION, this.pixelEmission),
            UI.labelRow(UIKeys.FORMS_EDITORS_MATERIAL_RELIEF, this.relief)
        );
    }

    private UISliderTrackpad pbrSlider(Consumer<Float> callback)
    {
        UISliderTrackpad slider = new UISliderTrackpad((v) -> callback.accept(v.floatValue()));

        slider.limit(0D, 1D);

        return slider;
    }

    /* Level plumbing */

    private ModelForm modelForm()
    {
        return this.form instanceof ModelForm modelForm ? modelForm : null;
    }

    private List<String> materials()
    {
        ModelForm modelForm = this.modelForm();
        ModelInstance model = modelForm == null ? null : ModelFormRenderer.getModel(modelForm);

        return model == null ? Collections.emptyList() : model.materials;
    }

    /** Whether the form has a real material set (2+ materials) worth its own level. */
    private boolean hasMaterials()
    {
        return this.materials().size() > 1;
    }

    /**
     * The edit target of the material level: the selected material's settings, or — the PBR
     * sliders at the whole-form level of a model — the default material's (empty key, the same
     * "no material" convention the texture resolver uses). Created on first edit.
     */
    private FormMaterial materialValue()
    {
        ModelForm modelForm = this.modelForm();

        return modelForm.materials.getOrCreate(this.material == null ? "" : this.material);
    }

    private ValueColor formColor()
    {
        return this.form != null && this.form.get("color") instanceof ValueColor color ? color : null;
    }

    private void applyColor(Color c)
    {
        if (this.material != null)
        {
            this.materialValue().color.set(c);
        }
        else
        {
            ValueColor color = this.formColor();

            if (color != null)
            {
                color.set(c);
            }
        }
    }

    private void applyOverlay(Color c)
    {
        if (this.material != null)
        {
            this.materialValue().overlayColor.set(c);
        }
        else
        {
            this.form.overlayColor.set(c);
        }
    }

    private void applyLighting(float value)
    {
        if (this.material != null)
        {
            this.materialValue().lighting.set(value);
        }
        else
        {
            this.form.lighting.set(value);
        }
    }

    private void openMaterialSelector()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.SPHERE, UIKeys.FORMS_EDITORS_MATERIAL_WHOLE_FORM, () -> this.pickMaterial(null));

            for (String material : this.materials())
            {
                if (material != null && !material.isEmpty())
                {
                    menu.action(Icons.MATERIAL, IKey.constant(material), () -> this.pickMaterial(material));
                }
            }
        });
    }

    private void pickMaterial(String material)
    {
        this.material = material;
        this.fill();
    }

    /**
     * Same picker flow the model panel's texture button had for materials: opens at the texture
     * currently in effect for the material, writes the pick into the form's per-material map.
     */
    private void openTexturePicker()
    {
        if (this.material == null)
        {
            return;
        }

        ModelForm modelForm = this.modelForm();
        ModelInstance model = ModelFormRenderer.getModel(modelForm);
        String material = this.material;
        Link link = modelForm.materialTextures.getLink(material);

        if (link == null && model != null)
        {
            Link fallback = modelForm.texture.get() != null ? modelForm.texture.get() : model.getTexture();

            link = model.getMaterialTexture(material, fallback);
        }

        UITexturePicker picker = UITexturePicker.open(this.getContext(), link, (l) -> modelForm.materialTextures.setLink(material, l));

        if (picker != null && modelForm.model.get() != null && !modelForm.model.get().isEmpty())
        {
            picker.withModelPreview(modelForm.model.get());
        }
    }

    /**
     * Refresh every widget from the selected level's values and rebuild what the level shows.
     * The column layout does NOT skip invisible children (hiding in place leaves gaps), so the
     * options tree is reassembled from scratch — same pattern as the model panel's shape keys.
     */
    private void fill()
    {
        boolean materialLevel = this.material != null;
        ModelForm modelForm = this.modelForm();

        this.materialSelector.label = materialLevel ? IKey.constant(this.material) : UIKeys.FORMS_EDITORS_MATERIAL_WHOLE_FORM;

        if (materialLevel)
        {
            FormMaterial value = modelForm.materials.getMaterial(this.material);

            this.color.setColor(value == null ? 0xffffffff : value.color.get().getARGBColor());
            this.overlay.setColor(value == null ? 0x00ffffff : value.overlayColor.get().getARGBColor());
            this.lighting.setValue(value == null ? 1F : value.lighting.get());
            this.culling.setValue(value == null ? FormMaterial.CULLING_MODEL : value.culling.get());
        }
        else
        {
            ValueColor color = this.formColor();

            this.color.setColor(color == null ? 0xffffffff : color.get().getARGBColor());
            this.overlay.setColor(this.form.overlayColor.get().getARGBColor());
            this.lighting.setValue(this.form.lighting.get());
            this.layer.setValue(this.form.renderLayer.get());
        }

        /* PBR edits go to the selected material, or the model's default material at form level. */
        FormMaterial pbr = modelForm == null ? null : modelForm.materials.getMaterial(materialLevel ? this.material : "");

        this.smoothness.setValue(pbr == null ? 0F : pbr.smoothness.get());
        this.metallic.setValue(pbr == null ? 0F : pbr.metallic.get());
        this.sss.setValue(pbr == null ? 0F : pbr.sss.get());
        this.pixelEmission.setValue(pbr == null ? 0F : pbr.pixelEmission.get());
        this.relief.setValue(pbr == null ? 0F : pbr.relief.get());

        /* Reassemble the tree for the level */
        this.materialSelector.removeFromParent();
        this.colorSection.removeFromParent();
        this.textureSection.removeFromParent();
        this.renderSection.removeFromParent();
        this.pbrSection.removeFromParent();
        this.colorRow.removeFromParent();
        this.layerRow.removeFromParent();
        this.cullingRow.removeFromParent();
        this.shaderShadow.removeFromParent();

        this.colorSection.fields.removeAll();
        this.renderSection.fields.removeAll();

        if (materialLevel || this.formColor() != null)
        {
            this.colorSection.fields.add(this.colorRow);
        }

        this.colorSection.fields.add(this.overlayRow, this.lightingRow);

        if (materialLevel)
        {
            this.renderSection.fields.add(this.cullingRow);
        }
        else
        {
            this.renderSection.fields.add(this.layerRow, this.shaderShadow);
        }

        if (this.hasMaterials())
        {
            this.options.add(this.materialSelector);
        }

        this.options.add(this.colorSection);

        if (materialLevel)
        {
            this.options.add(this.textureSection);
        }

        this.options.add(this.renderSection);

        if (modelForm != null)
        {
            this.options.add(this.pbrSection);
        }

        this.options.resize();
    }

    @Override
    public void startEdit(Form form)
    {
        super.startEdit(form);

        this.material = null;
        this.fill();
    }
}
