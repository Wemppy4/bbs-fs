package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.audio.Wave;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;
import org.lwjgl.openal.AL10;
import java.io.File;

/** Exercises production OpenAL ownership and texture-to-ffmpeg export in the actual GL context. */
public final class OriginalMediaProbe
{
    public static JsonObject run(JsonObject params) throws Exception
    {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        if (params.has("record") && params.get("record").getAsBoolean()) return record();
        File audio = BBSMod.getAssetsPath("audio/__aihelper_media.wav");
        audio.getParentFile().mkdirs();
        byte[] pcm = new byte[44100 * 2];
        for (int i = 0; i < 44100; i++)
        {
            short sample = (short) (Math.sin(2 * Math.PI * 440 * i / 44100) * 1200);
            pcm[i * 2] = (byte) sample; pcm[i * 2 + 1] = (byte) (sample >> 8);
        }
        WaveWriter.write(audio, new Wave(1, 1, 44100, 16, pcm));
        SoundManager manager = BBSModClient.getSounds();
        Object firstOwner = new Object(), secondOwner = new Object();
        SoundPlayer first = manager.playUnique(firstOwner, Link.assets("audio/__aihelper_media.wav"));
        SoundPlayer second = manager.playUnique(secondOwner, Link.assets("audio/__aihelper_media.wav"));
        try
        {
            result.addProperty("independentSources", first != null && second != null && first.getSource() != second.getSource());
            first.pause(); second.pause();
            first.setPlaybackPosition(0.2F); second.setPlaybackPosition(0.7F);
            result.addProperty("firstOffset", first.getPlaybackPosition());
            result.addProperty("secondOffset", second.getPlaybackPosition());
            result.addProperty("waveform", first.getBuffer().getWaveform().isCreated());
            manager.stopOwned(firstOwner);
            result.addProperty("secondSurvives", AL10.alIsSource(second.getSource()));
            result.addProperty("alError", AL10.alGetError());
            result.addProperty("glError", GL11.glGetError());
        }
        finally { manager.stopOwned(firstOwner); manager.stopOwned(secondOwner); }
        return result;
    }

    private static JsonObject record() throws Exception
    {
        String oldEncoder = BBSSettings.videoEncoderPath.get();
        String oldArguments = BBSSettings.videoArguments.get();
        int oldRate = BBSSettings.videoFrameRate.get(), oldBlur = BBSSettings.videoMotionBlur.get();
        boolean oldSound = BBSSettings.videoPlaySoundAfterExport.get(), oldFolder = BBSSettings.videoOpenFolderAfterExport.get();
        Texture texture = null;
        Pixels pixels = Pixels.fromSize(32, 24);
        VideoRecorder recorder = BBSModClient.getVideoRecorder();
        JsonObject result = new JsonObject();
        String name = "__aihelper_media_" + System.currentTimeMillis();
        try
        {
            BBSSettings.videoEncoderPath.set("C:/ffmpeg/bin/ffmpeg.exe");
            BBSSettings.videoArguments.set(BBSSettings.DEFAULT_FFMPEG_ARGUMENTS);
            BBSSettings.videoFrameRate.set(60);
            BBSSettings.videoMotionBlur.set(0);
            BBSSettings.videoPlaySoundAfterExport.set(false);
            BBSSettings.videoOpenFolderAfterExport.set(false);
            texture = new Texture();
            texture.setFilter(GL11.GL_NEAREST);
            pixels.rewindBuffer();
            texture.uploadTexture(GL11.GL_TEXTURE_2D, 0, 32, 24, pixels.getBuffer());
            recorder.startRecording(name, null, texture.id, 32, 24);
            result.addProperty("active", recorder.isRecording());
            int originalAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int originalRowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
            for (int frame = 0; frame < 18; frame++)
            {
                mchorse.bbs_mod.utils.colors.Color color = new mchorse.bbs_mod.utils.colors.Color();
                color.set(frame < 6 ? 0xffff0000 : frame < 12 ? 0xff00ff00 : 0xff0000ff);
                for (int y = 0; y < 24; y++) for (int x = 0; x < 32; x++) pixels.setColor(x, y, color);
                texture.bind();
                pixels.rewindBuffer();
                texture.uploadTexture(GL11.GL_TEXTURE_2D, 0, 32, 24, pixels.getBuffer());
                recorder.recordFrame();
            }
            result.addProperty("frames", recorder.getCounter());
            recorder.stopRecording(false);
            result.addProperty("stopped", !recorder.isRecording());
            result.addProperty("success", recorder.getFailure() == null);
            result.addProperty("pixelStoreRestored", originalAlignment == GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT)
                && originalRowLength == GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH));
            result.addProperty("path", new File(VideoRecorder.getVideoFolder(), name + ".mp4").getAbsolutePath());
            result.addProperty("glError", GL11.glGetError());
            result.addProperty("ok", recorder.getFailure() == null);
            return result;
        }
        finally
        {
            recorder.stopRecording(false);
            if (texture != null) texture.delete();
            pixels.delete();
            BBSSettings.videoEncoderPath.set(oldEncoder);
            BBSSettings.videoArguments.set(oldArguments);
            BBSSettings.videoFrameRate.set(oldRate);
            BBSSettings.videoMotionBlur.set(oldBlur);
            BBSSettings.videoPlaySoundAfterExport.set(oldSound);
            BBSSettings.videoOpenFolderAfterExport.set(oldFolder);
        }
    }
}
