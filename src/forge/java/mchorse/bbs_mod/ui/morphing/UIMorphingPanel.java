package mchorse.bbs_mod.ui.morphing;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.morphing.camera.ImmersiveMorphingCameraController;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.onboarding.TourAnchors;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import net.minecraft.client.Minecraft;

public class UIMorphingPanel extends UIDashboardPanel
{
    public UIFormPalette palette;
    public UIIcon demorph;
    public UIIcon fromMob;

    private ImmersiveMorphingCameraController controller;

    public UIMorphingPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.palette = new UIFormPalette(this::setForm);
        this.palette.updatable().cantExit();
        this.palette.immersive();
        this.palette.full(this);
        this.palette.editor.renderer.full(dashboard.getRoot());
        this.palette.noBackground();
        this.palette.canModify();

        MorphRenderer.setVisibility(() ->
        {
            UIBaseMenu menu = UIScreen.getCurrentMenu();

            if (menu instanceof UIDashboard)
            {
                UIDashboardPanel panel = ((UIDashboard) menu).getPanels().panel;

                if (panel instanceof UIModelBlockPanel)
                {
                    return false;
                }

                if (panel instanceof UIMorphingPanel)
                {
                    return !((UIMorphingPanel) panel).palette.editor.isEditing();
                }
            }

            return true;
        });

        this.demorph = new UIIcon(Icons.POSE, (b) ->
        {
            this.palette.setSelected(null);
            this.setForm(null);
        });
        this.demorph.tooltip(UIKeys.MORPHING_DEMORPH, Direction.TOP);
        this.fromMob = new UIIcon(Icons.MORPH, (b) ->
        {
            Form form = Morph.getMobForm(Minecraft.getMinecraft().player);

            if (form != null)
            {
                this.palette.setSelected(form);
                this.setForm(form);
            }
        });
        this.fromMob.tooltip(UIKeys.MORPHING_FROM_MOB, Direction.TOP);

        this.palette.list.bar.add(this.fromMob, this.demorph);

        this.add(this.palette);

        this.controller = new ImmersiveMorphingCameraController(() -> this.palette.editor.isEditing() ? this.palette.editor.renderer : null);

        this.onAppear(this::enterMorphing);
        this.onDisappear(this::leaveMorphing);

        /* What the tour of this panel points at */
        TourAnchors.register("morphing.forms", () -> this.palette.list.forms);
        TourAnchors.register("morphing.edit", () -> this.palette.list.edit);
        TourAnchors.register("morphing.demorph", () -> this.demorph);
    }

    private void enterMorphing()
    {
        this.palette.list.forms.scroll.scrollSpeed = 40;

        Morph morph = Morph.getMorph(Minecraft.getMinecraft().player);

        this.palette.list.setupForms(BBSModClient.getFormCategories());
        this.palette.setSelected(morph.getForm());

        BBSModClient.getCameraController().add(this.controller);
        Minecraft.getMinecraft().gameSettings.thirdPersonView = 1;

        if (BBSSettings.morphingFocusSearch.get())
        {
            Minecraft.getMinecraft().addScheduledTask(() -> this.palette.list.focusSearchInput());
        }
    }

    private void leaveMorphing()
    {
        BBSModClient.getCameraController().remove(this.controller);
        Minecraft.getMinecraft().gameSettings.thirdPersonView = 0;
    }

    private void setForm(Form form)
    {
        ClientNetwork.sendPlayerForm(form);
    }

    @Override
    public boolean needsBackground()
    {
        return !this.palette.editor.isEditing();
    }

}
