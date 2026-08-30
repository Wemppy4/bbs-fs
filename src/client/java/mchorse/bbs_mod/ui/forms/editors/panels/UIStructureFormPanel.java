package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.structure.StructureManager;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Main properties panel for {@link StructureForm}: structure file picker (scans the world's
 * {@code generated} folder), biome picker (client world's biome registry) and the origin
 * offset. The tint is not repeated here — the "Material" tab picks it up from the form's
 * {@code color} value like it does for every other form.
 */
public class UIStructureFormPanel extends UIFormPanel<StructureForm>
{
    public UIButton structure;
    public UIButton biome;
    public UITrackpad originX;
    public UITrackpad originY;
    public UITrackpad originZ;
    public UIToggle fastRender;

    public UIStructureFormPanel(UIForm editor)
    {
        super(editor);

        this.structure = new UIButton(L10n.lang("bbs.ui.forms.editors.structure.pick_structure"), (b) -> this.openStructurePicker());
        this.biome = new UIButton(L10n.lang("bbs.ui.forms.editors.structure.pick_biome"), (b) -> this.openBiomePicker());
        this.originX = this.createOriginTrackpad(Colors.RED, UIKeys.GENERAL_X);
        this.originY = this.createOriginTrackpad(Colors.GREEN, UIKeys.GENERAL_Y);
        this.originZ = this.createOriginTrackpad(Colors.BLUE, UIKeys.GENERAL_Z);
        this.fastRender = new UIToggle(L10n.lang("bbs.ui.forms.editors.structure.fast_render"), false, (b) -> this.form.fastRender.set(b.getValue()));
        this.fastRender.tooltip(L10n.lang("bbs.ui.forms.editors.structure.fast_render_desc"));

        /* Both buttons name what they pick, so they carry no label of their own */
        this.options.add(this.structure, this.biome);
        this.options.add(UI.label(L10n.lang("bbs.ui.forms.editors.structure.origin")), UI.row(this.originX, this.originY, this.originZ));
        this.options.add(this.fastRender);
    }

    /** One axis of the origin offset, colored like the axis it moves along. */
    private UITrackpad createOriginTrackpad(int color, IKey axis)
    {
        UITrackpad trackpad = new UITrackpad((v) -> this.updateOrigin());

        trackpad.block().onlyNumbers();
        trackpad.tooltip(IKey.constant("%s (%s)").format(L10n.lang("bbs.ui.forms.editors.structure.origin"), axis));
        trackpad.textbox.setColor(color);

        return trackpad;
    }

    private void updateOrigin()
    {
        this.form.origin.set(new Vector3f(
            (float) this.originX.getValue(),
            (float) this.originY.getValue(),
            (float) this.originZ.getValue()
        ));
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

        Vector3f origin = form.origin.get();

        this.originX.setValue(origin.x);
        this.originY.setValue(origin.y);
        this.originZ.setValue(origin.z);
        this.fastRender.setValue(form.fastRender.get());
    }
}
