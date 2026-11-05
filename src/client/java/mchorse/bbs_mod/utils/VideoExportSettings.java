package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;

import java.io.File;

/**
 * What a video export comes out like: its picture (size, frame rate, motion blur, held frames and
 * shutter), the encoder arguments, which sounds go into it, and where the file lands under which
 * name. A recording is handed these when it starts and keeps them to its end, so whatever exports
 * picks them - the film editor and the world recording take the ones in the settings
 * ({@link #global()}).
 *
 * <p>What stays the same for every export is not here and is read from the settings directly:
 * the encoder's path and log, the warm-up delay, the frame rate limit, muting the game while
 * rendering, and what happens once an export is done.
 */
public record VideoExportSettings(
    int width,
    int height,
    int frameRate,
    int motionBlur,
    int heldFrames,
    int shutter,
    String arguments,
    String argumentsAudio,
    String argumentsMux,
    boolean exportAudio,
    boolean minecraftSounds,
    String exportPath,
    String filenameFormat
)
{
    /**
     * The export settings of the "Video" settings category.
     */
    public static VideoExportSettings global()
    {
        return new VideoExportSettings(
            BBSSettings.videoWidth.get(),
            BBSSettings.videoHeight.get(),
            BBSSettings.videoFrameRate.get(),
            BBSSettings.videoMotionBlur.get(),
            BBSSettings.videoHeldFrames.get(),
            BBSSettings.videoShutter.get(),
            BBSSettings.videoArguments.get(),
            BBSSettings.videoArgumentsAudio.get(),
            BBSSettings.videoArgumentsMux.get(),
            BBSSettings.videoExportAudio.get(),
            BBSSettings.videoExportMinecraftSounds.get(),
            BBSSettings.videoExportPath.get(),
            BBSSettings.videoExportFilenameFormat.get()
        );
    }

    /**
     * The folder videos exported to the given path land in: the path itself when it is a
     * folder, otherwise (empty or missing) the movies folder next to the game's settings.
     */
    public static File resolveFolder(String exportPath)
    {
        File movies = new File(BBSMod.getSettingsFolder().getParentFile(), "movies");
        File path = new File(exportPath);

        if (path.isDirectory())
        {
            movies = path;
        }

        movies.mkdirs();

        return movies;
    }

    public File getFolder()
    {
        return resolveFolder(this.exportPath);
    }

    /**
     * The encoder arguments of the recording, with or without the film's audio track.
     */
    public String getArguments(boolean audio)
    {
        return audio ? this.argumentsAudio : this.arguments;
    }

    /**
     * How many times the frame rate gets doubled for the motion blur: each doubling is a render
     * more per video frame, averaged into it by the encoder. The motion blur option is the frame
     * rate it renders at, 2^(6 + option), none at 0.
     */
    public int getMotionBlur()
    {
        int target = this.motionBlur == 0 ? 0 : (int) Math.pow(2, 6 + this.motionBlur);
        double fps = this.frameRate;
        int i = 0;

        while (fps < target)
        {
            fps *= 2;

            i++;
        }

        return i;
    }

    /**
     * How many frames a second are rendered and handed to the encoder: the video's frame rate
     * times its motion blur sub-frames.
     */
    public int getCaptureFrameRate()
    {
        return this.frameRate * (1 << this.getMotionBlur());
    }

    /**
     * How many of a video frame's motion blur sub-frames go into it. With no shutter, all of them -
     * the blur spans the whole frame, as if the shutter were fully open (360 degrees), the way it
     * always worked; otherwise the share of the frame the shutter keeps open (180 = the film look).
     */
    public int getMotionBlurExposedFrames()
    {
        int subframes = 1 << this.getMotionBlur();

        return this.shutter <= 0 ? subframes : Math.max(1, Math.min(subframes, Math.round(subframes * this.shutter / 360F)));
    }

    /**
     * The part of a video frame's time that passes at its given held render. Each frame is
     * rendered "held frames" times; with no shutter they all show one moment, so the first moves
     * time a whole frame on and the rest none. The shutter (degrees, as on a film camera) spreads
     * them over that part of the frame instead - each repeat moves shutter/held of it, the first
     * the rest - and a shader that accumulates the repeats averages them into motion blur.
     */
    public float getHeldFrameShare(int heldFrame)
    {
        float step = this.heldFrames > 1 ? this.shutter / 360F / this.heldFrames : 0F;

        return heldFrame == 0 ? 1F - step * (this.heldFrames - 1) : step;
    }
}
