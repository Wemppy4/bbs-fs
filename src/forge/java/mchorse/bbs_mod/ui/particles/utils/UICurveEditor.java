package mchorse.bbs_mod.ui.particles.utils;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.particles.ParticleCurve;
import mchorse.bbs_mod.particles.ParticleCurveType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcons;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeSection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.Map;

public class UICurveEditor extends UIElement
{
    private UIParticleSchemeSection section;

    public UILabel name;
    public UIIcon rename;
    public UIIcon delete;
    public UIIcons type;
    public UICurve curve;
    public UIMolangExpression input;
    public UIMolangExpression range;

    private ParticleCurve particleCurve;

    public UICurveEditor(UIParticleSchemeSection section)
    {
        this.section = section;

        this.name = UI.label(IKey.EMPTY, 20).labelAnchor(0, 0.5F).background();
        this.rename = new UIIcon(Icons.EDIT, this::rename);
        this.rename.tooltip(UIKeys.SNOWSTORM_CURVES_RENAME);
        this.delete = new UIIcon(Icons.REMOVE, this::remove);
        this.delete.tooltip(UIKeys.SNOWSTORM_CURVES_REMOVE);
        this.type = new UIIcons((b) -> this.changeType(b.getValue()));

        for (ParticleCurveType type : ParticleCurveType.values())
        {
            this.type.add(curveTypeIcon(type), UIKeys.C_CURVE_TYPE.get(type.id));
        }

        this.curve = new UICurve(section);
        this.input = new UIMolangExpression(() -> this.particleCurve == null ? null : this.particleCurve.input, (b) -> this.section.editMoLang("curve." + this.particleCurve.variable.getName() + ".input", (str) -> this.particleCurve.input = this.section.parse(str, this.particleCurve.input), this.particleCurve.input));
        this.input.icon(Icons.IN).tooltip(UIKeys.SNOWSTORM_CURVES_INPUT);
        this.range = new UIMolangExpression(() -> this.particleCurve == null ? null : this.particleCurve.range, (b) -> this.section.editMoLang("curve." + this.particleCurve.variable.getName() + ".range", (str) -> this.particleCurve.range = this.section.parse(str, this.particleCurve.range), this.particleCurve.range));
        this.range.icon(Icons.OUT).tooltip(UIKeys.SNOWSTORM_CURVES_RANGE);

        this.curve.h(100);

        this.column().vertical().stretch();
        this.add(UI.row(0, this.name, this.rename, this.delete));
        this.add(UI.row(UI.label(UIKeys.SNOWSTORM_CURVES_TYPE, 20).labelAnchor(0, 0.5F), this.type));
        this.add(this.curve, this.input, this.range);
    }

    private static Icon curveTypeIcon(ParticleCurveType type)
    {
        return type == ParticleCurveType.HERMITE ? Icons.CURVES : Icons.INTERP_LINEAR;
    }

    private void rename(UIIcon b)
    {
        String oldName = this.particleCurve.variable.getName();
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_RENAME,
            UIKeys.SNOWSTORM_CURVES_RENAME_OVERLAY,
            (newName) ->
            {
                if (newName.isEmpty() || newName.contains(" "))
                {
                    return;
                }

                Map<String, ParticleCurve> curves = this.section.getScheme().curves;
                MolangParser parser = this.section.getScheme().parser;

                if (!parser.variables.containsKey(newName))
                {
                    parser.variables.put(newName, new Variable(newName, 0));
                }

                curves.put(newName, curves.remove(oldName));
                this.particleCurve.variable = parser.variables.get(newName);
                this.name.label = IKey.constant(newName);
                this.section.dirty();
            }
        );

        panel.text.setText(oldName);

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void remove(UIIcon b)
    {
        this.removeFromParent();
        this.section.getScheme().curves.remove(this.particleCurve.variable.getName());
        this.section.getEditor().resize();
        this.section.dirty();
    }

    private void changeType(int value)
    {
        this.particleCurve.type = ParticleCurveType.values()[value];
        this.section.dirty();
    }

    public void fill(ParticleCurve curve)
    {
        this.particleCurve = curve;

        this.name.label = IKey.constant(curve.variable.getName());
        this.type.setValue(curve.type.ordinal());
        this.curve.fill(curve);
    }
}