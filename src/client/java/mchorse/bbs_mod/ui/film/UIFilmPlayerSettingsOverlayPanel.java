package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageBarOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import net.minecraft.client.MinecraftClient;

public class UIFilmPlayerSettingsOverlayPanel extends UIMessageBarOverlayPanel
{
    private final Film film;

    public final UITrackpad hp;
    public final UITrackpad hunger;
    public final UITrackpad xpLevel;
    public final UITrackpad xpProgress;
    public final UITrackpad mobRecordingRadius;

    public final UIButton replaceInventory;
    public final UIButton applyToPlayer;
    public final UIScrollView editor;

    public UIFilmPlayerSettingsOverlayPanel(Film film)
    {
        super(UIKeys.FILM_PLAYER_SETTINGS_TITLE, UIKeys.FILM_PLAYER_SETTINGS_DESCRIPTION);

        this.film = film;

        this.message.removeFromParent();

        this.hp = new UITrackpad((v) -> BaseValue.edit(this.film.hp, (value) -> value.set(v.floatValue())));
        this.hp.limit(1, 20, true).setValue(this.film.hp.get());

        this.hunger = new UITrackpad((v) -> BaseValue.edit(this.film.hunger, (value) -> value.set(v.floatValue())));
        this.hunger.limit(1, 20, true).setValue(this.film.hunger.get());

        this.xpLevel = new UITrackpad((v) -> BaseValue.edit(this.film.xpLevel, (value) -> value.set(v.intValue())));
        this.xpLevel.limit(0).integer().setValue(this.film.xpLevel.get());

        this.xpProgress = new UITrackpad((v) -> BaseValue.edit(this.film.xpProgress, (value) -> value.set(v.floatValue())));
        this.xpProgress.limit(0, 1).increment(0.01D).setValue(this.film.xpProgress.get());

        this.mobRecordingRadius = new UITrackpad((v) -> BaseValue.edit(this.film.mobRecordingRadius, (value) -> value.set(v.floatValue())));
        this.mobRecordingRadius.limit(0).integer().setValue(this.film.mobRecordingRadius.get());
        this.mobRecordingRadius.tooltip(UIKeys.FILM_PLAYER_SETTINGS_MOB_RECORDING_RADIUS_TOOLTIP);

        this.replaceInventory = new UIButton(UIKeys.FILM_REPLACE_INVENTORY, (b) ->
            BaseValue.edit(this.film.inventory, (inv) -> inv.fromPlayer(MinecraftClient.getInstance().player)));
        this.replaceInventory.setEnabled(MinecraftClient.getInstance().player != null);

        this.applyToPlayer = new UIButton(UIKeys.FILM_APPLY_PLAYER_SETTINGS_TO_PLAYER, (b) -> ClientNetwork.sendApplyFilmPlayerSettingsToPlayer(this.film));
        this.applyToPlayer.setEnabled(MinecraftClient.getInstance().player != null);

        this.editor = UI.scrollView(3, 6,
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_HP, this.hp),
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_HUNGER, this.hunger),
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_XP_LEVEL, this.xpLevel),
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_XP_PROGRESS, this.xpProgress),
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_MOB_RECORDING_RADIUS, this.mobRecordingRadius),
            UI.row(this.replaceInventory, this.applyToPlayer).marginTop(UIConstants.SECTION_GAP)
        );
        this.editor.relative(this.content).x(6).w(1F, -12).y(6).hTo(this.bar.area, -6);

        this.content.add(this.editor);
    }
}
