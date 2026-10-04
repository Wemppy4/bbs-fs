package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.UIUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Forge texture readback and the original BBS ffmpeg recording lifecycle. */
public class VideoRecorder
{
    private Process process;
    private WritableByteChannel channel;
    private volatile boolean recording;
    private ByteBuffer buffer;
    private int textureId = -1;
    private int counter;
    private RuntimeException failure;
    public volatile int serverTicks;
    public volatile int lastServerTicks;

    public boolean isRecording() { return this.recording; }
    public int getTextureId() { return this.textureId; }
    public int getCounter() { return this.counter; }
    public RuntimeException getFailure() { return this.failure; }

    public static int getMotionBlur()
    {
        int target = BBSSettings.videoMotionBlur.get();
        target = target == 0 ? 0 : (int) Math.pow(2, 6 + target);
        int steps = 0;
        double fps = BBSSettings.videoFrameRate.get();
        while (fps < target) { fps *= 2; steps++; }
        return steps;
    }

    public static int getVideoFrameRate()
    {
        return BBSSettings.videoFrameRate.get() * (1 << getMotionBlur());
    }

    public static File getVideoFolder()
    {
        File configured = new File(BBSSettings.videoExportPath.get());
        File folder = configured.isDirectory() ? configured : new File(BBSMod.getSettingsFolder().getParentFile(), "movies");
        folder.mkdirs();
        return folder;
    }

    public void startRecording(String movieName, File audioFile, int textureId, int width, int height)
    {
        if (this.recording) return;
        if (width <= 0 || height <= 0 || textureId <= 0) throw new IllegalArgumentException("Invalid recording texture");
        this.failure = null;
        this.counter = 0;
        this.serverTicks = this.lastServerTicks = 0;
        this.textureId = textureId;
        this.buffer = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(width, height), 3));
        if (movieName == null || movieName.isEmpty()) movieName = StringUtils.createTimestampFilename();
        String parameters = audioFile == null ? BBSSettings.videoArguments.get() : BBSSettings.videoArgumentsAudio.get();
        StringBuilder filters = new StringBuilder("vflip");
        for (int i = 0; i < getMotionBlur(); i++) filters.append(",tblend=all_mode=average,framestep=2");
        List<String> args = new ArrayList<>();
        args.add(FFMpegUtils.getFFMPEG());
        for (String arg : parameters.split(" "))
        {
            if (arg.isEmpty()) continue;
            arg = arg.replace("%WIDTH%", String.valueOf(width)).replace("%HEIGHT%", String.valueOf(height))
                .replace("%FPS%", String.valueOf((float) getVideoFrameRate())).replace("%NAME%", movieName)
                .replace("%FILTERS%", filters.toString());
            if (audioFile != null) arg = arg.replace("%AUDIO_TRACK%", audioFile.getAbsolutePath());
            args.add(arg);
        }
        File folder = getVideoFolder();
        File log = BBSSettings.videoEncoderLog.get() ? new File(folder, movieName + ".log") : BBSMod.getSettingsPath("video.log");
        try
        {
            log.getParentFile().mkdirs();
            this.process = new ProcessBuilder(args).directory(folder).redirectErrorStream(true).redirectOutput(log).start();
            this.channel = Channels.newChannel(this.process.getOutputStream());
            this.recording = true;
            UIUtils.playClick(2F);
        }
        catch (IOException e)
        {
            this.buffer = null;
            this.textureId = -1;
            this.failure = new IllegalStateException("Cannot start ffmpeg; see " + log, e);
            throw this.failure;
        }
    }

    /** Every requested frame is fully written, including the final one. */
    public void recordFrame()
    {
        if (!this.recording) return;
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
            this.buffer.clear();
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, this.buffer);
            this.buffer.rewind();
            while (this.buffer.hasRemaining()) this.channel.write(this.buffer);
            this.counter++;
        }
        catch (IOException e)
        {
            this.failure = new IllegalStateException("ffmpeg stopped receiving video frames", e);
            this.stopRecording(false);
            throw this.failure;
        }
        finally
        {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL11.glPopClientAttrib();
        }
    }

    public void stopRecording() { this.stopRecording(true); }

    public void stopRecording(boolean finishEffects)
    {
        if (!this.recording) return;
        this.recording = false;
        try
        {
            if (this.channel != null) this.channel.close();
            if (this.process != null)
            {
                if (!this.process.waitFor(1, TimeUnit.MINUTES))
                    this.failure = new IllegalStateException("ffmpeg did not finish within one minute");
                else if (this.process.exitValue() != 0)
                    this.failure = new IllegalStateException("ffmpeg failed with exit code " + this.process.exitValue());
            }
        }
        catch (IOException e) { this.failure = new IllegalStateException("Cannot finalize recording", e); }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            this.failure = new IllegalStateException("Recording interrupted", e);
        }
        finally
        {
            if (this.process != null) this.process.destroy();
            this.process = null;
            this.channel = null;
            this.buffer = null;
            this.textureId = -1;
            this.serverTicks = this.lastServerTicks = 0;
        }
        if (this.failure != null) BBSMod.LOGGER.error("Video recording failed", this.failure);
        else if (finishEffects) this.playFinishEffects();
    }

    public void playFinishEffects()
    {
        if (BBSSettings.videoPlaySoundAfterExport.get()
            && BBSModClient.getSounds().play(Link.assets("sounds/render_complete.ogg")) == null) UIUtils.playClick(0.5F);
        if (BBSSettings.videoOpenFolderAfterExport.get()) Minecraft.getMinecraft().addScheduledTask(() -> UIUtils.openFolder(getVideoFolder()));
    }

    public void toggleRecording(int textureId, int width, int height)
    {
        if (this.recording) this.stopRecording();
        else this.startRecording(StringUtils.createTimestampFilename(), null, textureId, width, height);
        UIUtils.playClick();
    }
}
