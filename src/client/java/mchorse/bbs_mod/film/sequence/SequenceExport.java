package mchorse.bbs_mod.film.sequence;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.VideoExportSettings;

/**
 * A sequence's export settings. Off by default: the sequence records with the settings' "Video"
 * category like everything else. With {@link #custom} on, the picture, the sound and the folder
 * are the sequence's own (they start as a copy of the global ones, {@link #copyGlobal()}); the
 * encoder's arguments and the file name format stay global.
 *
 * <p>The ids are the ones of the settings, so the labels are the settings' too.
 */
public class SequenceExport extends ValueGroup
{
    public final ValueBoolean custom = new ValueBoolean("custom", false);
    public final ValueInt width = new ValueInt("width", 1280, 2, 8096);
    public final ValueInt height = new ValueInt("height", 720, 2, 8096);
    public final ValueInt frameRate = new ValueInt("frame_rate", 60, 10, 1000);
    public final ValueInt motionBlur = new ValueInt("motion_blur", 0, 0, 6);
    public final ValueInt heldFrames = new ValueInt("held_frames", 1, 1, 1000);
    public final ValueInt shutter = new ValueInt("shutter", 0, 0, 360);
    public final ValueBoolean audio = new ValueBoolean("audio", true);
    public final ValueBoolean minecraftSounds = new ValueBoolean("minecraft_sounds", false);
    public final ValueString exportPath = new ValueString("export_path", "");

    public SequenceExport(String id)
    {
        super(id);

        this.add(this.custom);
        this.add(this.width);
        this.add(this.height);
        this.add(this.frameRate);
        this.add(this.motionBlur);
        this.add(this.heldFrames);
        this.add(this.shutter);
        this.add(this.audio);
        this.add(this.minecraftSounds);
        this.add(this.exportPath);
    }

    /**
     * Take over the export settings of the settings' "Video" category.
     */
    public void copyGlobal()
    {
        VideoExportSettings global = VideoExportSettings.global();

        this.width.set(global.width());
        this.height.set(global.height());
        this.frameRate.set(global.frameRate());
        this.motionBlur.set(global.motionBlur());
        this.heldFrames.set(global.heldFrames());
        this.shutter.set(global.shutter());
        this.audio.set(global.exportAudio());
        this.minecraftSounds.set(global.minecraftSounds());
        this.exportPath.set(global.exportPath());
    }

    public VideoExportSettings toExportSettings()
    {
        VideoExportSettings global = VideoExportSettings.global();

        if (!this.custom.get())
        {
            return global;
        }

        return new VideoExportSettings(
            this.width.get(),
            this.height.get(),
            this.frameRate.get(),
            this.motionBlur.get(),
            this.heldFrames.get(),
            this.shutter.get(),
            global.arguments(),
            global.argumentsAudio(),
            global.argumentsMux(),
            this.audio.get(),
            this.minecraftSounds.get(),
            this.exportPath.get(),
            global.filenameFormat()
        );
    }

    /**
     * Everything that changes how a recorded piece looks, boiled down: a piece recorded under
     * another stamp is stale. Sound and file names don't count, the pieces are put together anew
     * every time anyway. Taken from the settings in use, so with the global ones a change in the
     * settings' "Video" category makes the pieces stale too.
     */
    public String getPictureStamp()
    {
        VideoExportSettings s = this.toExportSettings();
        String picture = s.width() + "x" + s.height() + "@" + s.frameRate()
            + "|" + s.motionBlur() + "|" + s.heldFrames() + "|" + s.shutter()
            + "|" + s.arguments() + "|" + s.argumentsAudio();

        return Integer.toHexString(picture.hashCode());
    }
}
