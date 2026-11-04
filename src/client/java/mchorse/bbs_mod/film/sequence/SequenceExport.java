package mchorse.bbs_mod.film.sequence;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.VideoExportSettings;

/**
 * A sequence's own export settings: what the video comes out like and where it lands. They
 * start as a copy of the settings' "Video" category ({@link #copyGlobal()}) and are the
 * sequence's own from then on, so the pieces it recorded don't go stale when someone records
 * something else at another size.
 *
 * <p>The ids are the ones of the settings, so the labels are the settings' too.
 */
public class SequenceExport extends ValueGroup
{
    public final ValueInt width = new ValueInt("width", 1280, 2, 8096);
    public final ValueInt height = new ValueInt("height", 720, 2, 8096);
    public final ValueInt frameRate = new ValueInt("frame_rate", 60, 10, 1000);
    public final ValueInt motionBlur = new ValueInt("motion_blur", 0, 0, 6);
    public final ValueInt heldFrames = new ValueInt("held_frames", 1, 1, 1000);
    public final ValueInt shutter = new ValueInt("shutter", 0, 0, 360);
    public final ValueString arguments = new ValueString("arguments", BBSSettings.DEFAULT_FFMPEG_ARGUMENTS);
    public final ValueString argumentsAudio = new ValueString("arguments_audio", BBSSettings.DEFAULT_AUDIO_FFMPEG_ARGUMENTS);
    public final ValueString argumentsMux = new ValueString("arguments_mux", BBSSettings.DEFAULT_MUX_FFMPEG_ARGUMENTS);
    public final ValueBoolean audio = new ValueBoolean("audio", true);
    public final ValueBoolean minecraftSounds = new ValueBoolean("minecraft_sounds", false);
    public final ValueString exportPath = new ValueString("export_path", "");
    public final ValueString filenameFormat = new ValueString("filename_format", "{name}");

    public SequenceExport(String id)
    {
        super(id);

        this.add(this.width);
        this.add(this.height);
        this.add(this.frameRate);
        this.add(this.motionBlur);
        this.add(this.heldFrames);
        this.add(this.shutter);
        this.add(this.arguments);
        this.add(this.argumentsAudio);
        this.add(this.argumentsMux);
        this.add(this.audio);
        this.add(this.minecraftSounds);
        this.add(this.exportPath);
        this.add(this.filenameFormat);
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
        this.arguments.set(global.arguments());
        this.argumentsAudio.set(global.argumentsAudio());
        this.argumentsMux.set(global.argumentsMux());
        this.audio.set(global.exportAudio());
        this.minecraftSounds.set(global.minecraftSounds());
        this.exportPath.set(global.exportPath());
        this.filenameFormat.set(global.filenameFormat());
    }

    public VideoExportSettings toExportSettings()
    {
        return new VideoExportSettings(
            this.width.get(),
            this.height.get(),
            this.frameRate.get(),
            this.motionBlur.get(),
            this.heldFrames.get(),
            this.shutter.get(),
            this.arguments.get(),
            this.argumentsAudio.get(),
            this.argumentsMux.get(),
            this.audio.get(),
            this.minecraftSounds.get(),
            this.exportPath.get(),
            this.filenameFormat.get()
        );
    }

    /**
     * Everything that changes how a recorded piece looks, boiled down: a piece recorded under
     * another stamp is stale. Sound and file names don't count, the pieces are put together anew
     * every time anyway.
     */
    public String getPictureStamp()
    {
        String picture = this.width.get() + "x" + this.height.get() + "@" + this.frameRate.get()
            + "|" + this.motionBlur.get() + "|" + this.heldFrames.get() + "|" + this.shutter.get()
            + "|" + this.arguments.get() + "|" + this.argumentsAudio.get();

        return Integer.toHexString(picture.hashCode());
    }
}
