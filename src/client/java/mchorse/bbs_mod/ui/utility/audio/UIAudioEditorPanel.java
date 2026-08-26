package mchorse.bbs_mod.ui.utility.audio;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIEditorDashboardPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIAudioEditorPanel extends UIEditorDashboardPanel
{
    public UIIcon pickAudio;
    public UIIcon plause;
    public UIIcon saveColors;
    public UIAudioEditor audioEditor;

    public UIAudioEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.pickAudio = new UIIcon(Icons.MORE, (b) -> UIOverlay.addOverlay(this.getContext(), new UISoundOverlayPanel(this::openAudio)));
        this.plause = new UIIcon(() ->
        {
            SoundPlayer player = this.audioEditor.getPlayer();

            if (player == null)
            {
                return Icons.STOP;
            }

            return player.isPlaying() ? Icons.PAUSE : Icons.PLAY;
        }, (b) -> this.audioEditor.togglePlayback());
        this.saveColors = new UIIcon(Icons.SAVED, (b) -> this.saveColors());
        this.audioEditor = new UIAudioEditor();
        this.audioEditor.full(this.editor);

        this.actions().action(this.plause).common(this.saveColors).menu(this.pickAudio);
        this.add(this.audioEditor);

        this.openAudio(null);

        this.keys().register(Keys.PLAUSE, this.audioEditor::togglePlayback);
        this.keys().register(Keys.SAVE, this::saveColors);
        this.keys().register(Keys.OPEN_DATA_MANAGER, this.pickAudio::clickItself);
    }

    @Override
    public void requestNames()
    {}

    /* Tabs — a tab holds the link of an open sound */

    @Override
    public void openTab(String id)
    {
        this.setAudio(id == null ? null : Link.create(id));
    }

    @Override
    public String getOpenId()
    {
        Link audio = this.audioEditor.getAudio();

        return audio == null ? null : audio.toString();
    }

    @Override
    public Icon getTabIcon(String id)
    {
        return id == null ? Icons.SEARCH : Icons.SOUND;
    }

    /** Picked from the sound overlay: goes into the current tab, like picking data does elsewhere. */
    private void openAudio(Link link)
    {
        this.tabs.pick(link == null ? null : link.toString());
    }

    private void setAudio(Link link)
    {
        this.audioEditor.setup(link);
        this.saveColors.setEnabled(this.audioEditor.isEditing());
    }

    private void saveColors()
    {
        Link audio = this.audioEditor.getAudio();

        if (audio == null)
        {
            return;
        }

        SoundManager sounds = BBSModClient.getSounds();

        sounds.saveColorCodes(new Link(audio.source, audio.path + ".json"), this.audioEditor.getColorCodes());
        sounds.deleteSound(audio);
    }
}
