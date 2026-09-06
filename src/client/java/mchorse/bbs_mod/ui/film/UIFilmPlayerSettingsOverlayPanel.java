package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageBarOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public class UIFilmPlayerSettingsOverlayPanel extends UIMessageBarOverlayPanel
{
    private final Film film;
    private final int tick;

    public final UISliderTrackpad hp;
    public final UISliderTrackpad hunger;
    public final UITrackpad xpLevel;
    public final UISliderTrackpad xpProgress;
    public final UITrackpad mobRecordingRadius;

    public final UIButton recordHotbar;
    public final UIButton applyToPlayer;
    public final UIScrollView editor;

    public UIFilmPlayerSettingsOverlayPanel(Film film, int tick)
    {
        super(UIKeys.FILM_PLAYER_SETTINGS_TITLE, UIKeys.FILM_PLAYER_SETTINGS_DESCRIPTION);

        this.film = film;
        this.tick = tick;

        this.message.removeFromParent();

        this.hp = new UISliderTrackpad((v) -> BaseValue.edit(this.film.hp, (value) -> value.set(v.floatValue())));
        this.hp.limit(1, 20, true);
        this.hp.valueBinding(() -> this.hp.setValue(this.film.hp.get()));

        this.hunger = new UISliderTrackpad((v) -> BaseValue.edit(this.film.hunger, (value) -> value.set(v.floatValue())));
        this.hunger.limit(1, 20, true);
        this.hunger.valueBinding(() -> this.hunger.setValue(this.film.hunger.get()));

        this.xpLevel = new UITrackpad((v) -> BaseValue.edit(this.film.xpLevel, (value) -> value.set(v.intValue())));
        this.xpLevel.limit(0).integer();
        this.xpLevel.valueBinding(() -> this.xpLevel.setValue(this.film.xpLevel.get()));

        this.xpProgress = new UISliderTrackpad((v) -> BaseValue.edit(this.film.xpProgress, (value) -> value.set(v.floatValue())));
        this.xpProgress.limit(0, 1).increment(0.01D);
        this.xpProgress.valueBinding(() -> this.xpProgress.setValue(this.film.xpProgress.get()));

        this.mobRecordingRadius = new UITrackpad((v) -> BaseValue.edit(this.film.mobRecordingRadius, (value) -> value.set(v.floatValue())));
        this.mobRecordingRadius.limit(0).integer();
        this.mobRecordingRadius.valueBinding(() -> this.mobRecordingRadius.setValue(this.film.mobRecordingRadius.get()));
        this.mobRecordingRadius.tooltip(UIKeys.FILM_PLAYER_SETTINGS_MOB_RECORDING_RADIUS_TOOLTIP);

        this.recordHotbar = new UIButton(UIKeys.FILM_RECORD_HOTBAR, (b) -> this.recordHotbar());
        this.recordHotbar.tooltip(UIKeys.FILM_RECORD_HOTBAR_TOOLTIP);
        this.recordHotbar.setEnabled(MinecraftClient.getInstance().player != null && film.getFirstPersonReplay() != null);

        this.applyToPlayer = new UIButton(UIKeys.FILM_APPLY_PLAYER_SETTINGS_TO_PLAYER, (b) -> ClientNetwork.sendApplyFilmPlayerSettingsToPlayer(this.film, this.tick));
        this.applyToPlayer.setEnabled(MinecraftClient.getInstance().player != null);

        this.editor = UI.scrollView(3, 6,
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_HP, this.hp),
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_HUNGER, this.hunger),
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_XP_LEVEL, this.xpLevel),
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_XP_PROGRESS, this.xpProgress),
            UI.labelRow(UIKeys.FILM_PLAYER_SETTINGS_MOB_RECORDING_RADIUS, this.mobRecordingRadius),
            UI.row(this.recordHotbar, this.applyToPlayer).marginTop(UIConstants.SECTION_GAP)
        );
        this.editor.relative(this.content).x(6).w(1F, -12).y(6).hTo(this.bar.area, -6);

        this.content.add(this.editor);
    }

    /**
     * Key the player's hotbar as it is right now into the first person replay at the cursor.
     * The film has no inventory of its own anymore - the hotbar is nine channels on the
     * replay - so this is where "give the film my items" now writes to.
     */
    private void recordHotbar()
    {
        Replay replay = this.film.getFirstPersonReplay();
        PlayerEntity player = MinecraftClient.getInstance().player;

        if (replay == null || player == null)
        {
            return;
        }

        BaseValue.edit(replay.keyframes, (keyframes) ->
        {
            for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE; i++)
            {
                keyframes.hotbar.get(i).insert(this.tick, player.getInventory().getStack(i).copy());
            }

            keyframes.selectedSlot.insert(this.tick, player.getInventory().selectedSlot);
        });
    }
}
