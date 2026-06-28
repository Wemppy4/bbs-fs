package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.structure.StructureManager;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Main properties panel for {@link StructureForm}: structure file picker (scans the world's
 * {@code generated} folder) and biome picker (client world's biome registry).
 */
public class UIStructureFormPanel extends UIFormPanel<StructureForm>
{
    public UIButton structure;
    public UIButton biome;
    public UIColor color;
    public UIToggle fastRender;

    public UIStructureFormPanel(UIForm editor)
    {
        super(editor);

        this.structure = new UIButton(L10n.lang("bbs.ui.forms.editors.structure.pick_structure"), (b) -> this.openStructurePicker());
        this.biome = new UIButton(L10n.lang("bbs.ui.forms.editors.structure.pick_biome"), (b) -> this.openBiomePicker());
        this.color = new UIColor((c) -> this.form.color.set(Color.rgba(c))).withAlpha();
        this.fastRender = new UIToggle(L10n.lang("bbs.ui.forms.editors.structure.fast_render"), false, (b) -> this.form.fastRender.set(b.getValue()));
        this.fastRender.tooltip(L10n.lang("bbs.ui.forms.editors.structure.fast_render_desc"));

        this.options.add(UI.label(L10n.lang("bbs.ui.forms.editors.structure.structure")), this.structure);
        this.options.add(UI.label(L10n.lang("bbs.ui.forms.editors.structure.biome")), this.biome);
        this.options.add(UI.label(L10n.lang("bbs.ui.forms.editors.structure.color")), this.color);
        this.options.add(this.fastRender);
    }

    private void openStructurePicker()
    {
        /* Re-scan so structures saved after the world was opened (or re-saved) show up */
        StructureManager.invalidate();

        UIStringOverlayPanel panel = new UIStringOverlayPanel(
            L10n.lang("bbs.ui.forms.editors.structure.pick_structure"),
            StructureManager.getStructureIds(),
            (str) -> this.form.structure.set(str == null ? "" : str)
        );

        panel.set(this.form.structure.get());
        UIOverlay.addOverlay(this.getContext(), panel, 240, 280);
    }

    private void openBiomePicker()
    {
        List<String> ids = new ArrayList<>();
        ClientWorld world = MinecraftClient.getInstance().world;

        if (world != null)
        {
            for (Identifier id : world.getRegistryManager().get(RegistryKeys.BIOME).getIds())
            {
                ids.add(id.toString());
            }
        }

        UIStringOverlayPanel panel = new UIStringOverlayPanel(
            L10n.lang("bbs.ui.forms.editors.structure.pick_biome"),
            false,
            ids,
            (str) -> this.form.biome.set(str)
        );

        panel.set(this.form.biome.get());
        UIOverlay.addOverlay(this.getContext(), panel, 240, 280);
    }

    @Override
    public void startEdit(StructureForm form)
    {
        super.startEdit(form);

        this.color.setColor(form.color.get().getARGBColor());
        this.fastRender.setValue(form.fastRender.get());
    }
}
