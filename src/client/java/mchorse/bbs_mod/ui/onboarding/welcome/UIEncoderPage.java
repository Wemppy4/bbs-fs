package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.OS;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.File;
import java.util.Optional;

/**
 * "Without it BBS can't save video": whether the encoder is there, told by running it. One
 * line of answer — found (with the version it reported), missing, or being asked — the path
 * under it, and only the buttons the answer calls for: point at the file, and, while it is
 * missing, where to get it. Asking again isn't a button; it happens whenever the path changes.
 *
 * <p>The version is the only proof worth showing. A path that exists can be the wrong file,
 * and that is otherwise found out half an hour later on export.</p>
 */
public class UIEncoderPage extends UIWelcomePage
{
    private static final String DOWNLOAD_LINK = "https://ffmpeg.org/download.html";

    private static final int STATUS_H = 20;
    private static final int BUTTON_W = 120;
    private static final int BUTTON_GAP = 4;

    private enum State
    {
        CHECKING, FOUND, MISSING
    }

    private final UIText path;
    private final UIButton pick;
    private final UIButton download;

    private volatile State state;
    private volatile String version;

    /** The state the buttons were last laid out for; the check answers off the render thread. */
    private State laidOut;

    public UIEncoderPage()
    {
        super(UIKeys.ONBOARDING_ENCODER_TITLE, UIKeys.ONBOARDING_ENCODER_SLOGAN);

        UIElement status = new UIElement();

        status.h(STATUS_H);
        status.add(new UIRenderable((context) -> this.renderStatus(context, status.area)));

        this.path = new UIText().color(MUTED, false).lineHeight(12).textAnchorX(0.5F);
        this.updatePath();

        /* Placed by hand rather than as a row: one button sits in the middle, two sit either
         * side of it, and which it is changes with the answer */
        UIElement buttons = new UIElement();

        this.pick = new UIButton(UIKeys.ONBOARDING_ENCODER_PICK, (b) -> this.pick());
        this.download = new UIButton(UIKeys.ONBOARDING_ENCODER_DOWNLOAD, (b) -> UIUtils.openWebLink(DOWNLOAD_LINK));
        this.pick.relative(buttons).y(0).w(BUTTON_W);
        this.download.relative(buttons).x(0.5F, BUTTON_GAP / 2).y(0).w(BUTTON_W);
        this.download.setVisible(false);

        buttons.h(UIConstants.CONTROL_HEIGHT);
        buttons.add(this.pick, this.download);

        this.body.column(8).vertical().stretch();
        this.body.add(status, this.path, new UIElement().h(4), buttons);
    }

    /** Asked once, when the tab first comes up; a new path asks again. */
    @Override
    public void onShown()
    {
        if (this.state == null)
        {
            this.check();
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.laidOut != this.state)
        {
            boolean missing = this.state == State.MISSING;

            this.laidOut = this.state;
            this.download.setVisible(missing);

            if (missing)
            {
                this.pick.x(0.5F, -BUTTON_GAP / 2).anchorX(1F);
            }
            else
            {
                this.pick.x(0.5F).anchorX(0.5F);
            }

            this.body.resize();
        }

        super.render(context);
    }

    private void updatePath()
    {
        this.path.text(IKey.constant(BBSSettings.videoEncoderPath.get()));
        this.body.resize();
    }

    /** Runs the encoder off the render thread; the answer lands whenever it lands. */
    private void check()
    {
        this.state = State.CHECKING;

        Thread thread = new Thread(() ->
        {
            Optional<String> version = FFMpegUtils.version();

            this.version = version.orElse(null);
            this.state = version.isPresent() ? State.FOUND : State.MISSING;
        }, "BBS ffmpeg check");

        thread.setDaemon(true);
        thread.start();
    }

    private void pick()
    {
        /* Only Windows names the binary by extension, so there is nothing to narrow the list
         * down by anywhere else — the same bargain the settings row makes */
        String[] patterns = OS.CURRENT == OS.WINDOWS ? new String[] {"*.exe"} : null;
        File current = new File(BBSSettings.videoEncoderPath.get());

        UIFileDialogs.pickFile(UIKeys.GENERAL_DIALOG_ENCODER, current.isFile() ? current : null, patterns, UIKeys.GENERAL_DIALOG_ENCODER, (file) ->
        {
            if (file != null)
            {
                BBSSettings.videoEncoderPath.set(file.getAbsolutePath());
                this.updatePath();
                this.check();
            }
        });
    }

    private void renderStatus(UIContext context, Area area)
    {
        FontRenderer font = context.batcher.getFont();
        State state = this.state == null ? State.CHECKING : this.state;
        Icon icon;
        int color;
        String label;

        switch (state)
        {
            case FOUND ->
            {
                icon = Icons.CHECKMARK;
                color = Colors.A100 | Colors.GREEN;
                label = UIKeys.ONBOARDING_ENCODER_FOUND.format(this.version).get();
            }
            case MISSING ->
            {
                icon = Icons.EXCLAMATION;
                color = Colors.A100 | Colors.RED;
                label = UIKeys.ONBOARDING_ENCODER_MISSING.get();
            }
            default ->
            {
                icon = Icons.REFRESH;
                color = DIMMED;
                label = UIKeys.ONBOARDING_ENCODER_CHECKING.get();
            }
        }

        /* The icon and the words are centered as one thing */
        label = font.limitToWidth(label, area.w - 24);

        int x = area.mx() - (font.getWidth(label) + 20) / 2;

        context.batcher.icon(icon, color, x, area.my(), 0F, 0.5F);
        context.batcher.text(label, x + 20, area.my() - font.getHeight() / 2, Colors.WHITE, false);
    }
}
