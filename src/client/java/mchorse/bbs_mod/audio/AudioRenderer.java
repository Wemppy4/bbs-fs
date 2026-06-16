package mchorse.bbs_mod.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.wav.WaveWriter;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AudioRenderer
{
    private static final float WAVEFORM_PAST_BRIGHTNESS = 0.45F;
    private static final int WAVEFORM_PLAYHEAD_COLOR = 0xff57f52a;

    /**
     * One preview bar with every audible clip layered in the same strip, using the same scrolling window as
     * {@link #renderWaveform}: playhead stays centered; left = past (dim), right = future; the window moves with {@code tick}.
     */
    public static void renderPreviewCombined(Batcher2D batcher, List<AudioClip> clips, float tick, int x, int y, int w, int h, int sw, int sh)
    {
        int dimColor = waveformDimColor();

        drawWaveformPanelChrome(batcher, x, y, w, h);

        int innerX = x + 2;
        int innerY = y + 2;
        int innerW = w - 4;
        int half = w / 2;
        int centerX = x + half;

        int pps = resolveWaveformPixelsPerSecond(clips);

        /* Same visible time span as renderWaveform (full panel width w, not inner) */
        float duration = w / (float) pps;
        float windowTicks = duration * 20F;
        float visibleStart = tick - windowTicks / 2F;
        float visibleEnd = tick + windowTicks / 2F;

        batcher.clip(innerX, innerY, innerW, h - 4, sw, sh);

        for (AudioClip clip : clips)
        {
            SoundBuffer audio = BBSModClient.getSounds().get(clip.audio.get(), true);

            if (audio == null || audio.getWaveform() == null)
            {
                continue;
            }

            Waveform wave = audio.getWaveform();
            int clipDurTicks = Math.min((int) (wave.getDuration() * 20), clip.duration.get());
            float clipStart = clip.tick.get();
            float clipEnd = clipStart + clipDurTicks;

            float s1 = Math.max(visibleStart, clipStart);
            float s2 = Math.min(visibleEnd, clipEnd);

            if (s1 >= s2)
            {
                continue;
            }

            float rel1 = half + (s1 - tick) * w / (duration * 20F);
            float rel2 = half + (s2 - tick) * w / (duration * 20F);

            int drawX1 = x + (int) rel1;
            int drawX2 = x + (int) Math.ceil(rel2);

            drawX1 = Math.max(innerX, drawX1);
            drawX2 = Math.min(innerX + innerW, Math.max(drawX1 + 1, drawX2));

            float a1 = TimeUtils.toSeconds(s1 - clip.tick.get() + clip.offset.get());
            float a2 = TimeUtils.toSeconds(s2 - clip.tick.get() + clip.offset.get());
            float aCenter = TimeUtils.toSeconds(tick - clip.tick.get() + clip.offset.get());

            float maxA = wave.getDuration();
            a1 = Math.max(0F, Math.min(maxA, a1));
            a2 = Math.max(0F, Math.min(maxA, a2));
            aCenter = Math.max(0F, Math.min(maxA, aCenter));

            if (a2 <= a1)
            {
                continue;
            }

            if (drawX2 <= centerX)
            {
                wave.render(batcher, dimColor, drawX1, y, drawX2 - drawX1, h, a1, a2);
            }
            else if (drawX1 >= centerX)
            {
                wave.render(batcher, Colors.WHITE, drawX1, y, drawX2 - drawX1, h, a1, a2);
            }
            else
            {
                wave.render(batcher, dimColor, drawX1, y, centerX - drawX1, h, a1, aCenter);
                wave.render(batcher, Colors.WHITE, centerX, y, drawX2 - centerX, h, aCenter, a2);
            }
        }

        batcher.unclip(sw, sh);

        drawWaveformPlayhead(batcher, x, y, w, h);

        ActiveAudioAtTick active = findActiveAudioAtTick(clips, tick);

        if (BBSSettings.audioWaveformFilename.get() && active != null && active.buffer != null)
        {
            batcher.textCard(active.buffer.getId().toString(), x + 8, y + h / 2 - 4, 0xffffff, 0x99000000);
        }

        if (BBSSettings.audioWaveformTime.get())
        {
            float playback = active != null
                ? TimeUtils.toSeconds(tick - active.clip.tick.get() + active.clip.offset.get())
                : TimeUtils.toSeconds(tick);

            drawWaveformTimeLabel(batcher, tick, playback, x, y, w, h);
        }
    }

    private static int waveformDimColor()
    {
        return Colors.COLOR.set(WAVEFORM_PAST_BRIGHTNESS, WAVEFORM_PAST_BRIGHTNESS, WAVEFORM_PAST_BRIGHTNESS, 1F).getARGBColor();
    }

    private static int resolveWaveformPixelsPerSecond(List<AudioClip> clips)
    {
        for (AudioClip clip : clips)
        {
            SoundBuffer audio = BBSModClient.getSounds().get(clip.audio.get(), true);

            if (audio != null && audio.getWaveform() != null)
            {
                int pps = audio.getWaveform().getPixelsPerSecond();

                if (pps > 0)
                {
                    return pps;
                }
            }
        }

        return BBSSettings.audioWaveformDensity.get();
    }

    private static void drawWaveformPlayhead(Batcher2D batcher, int x, int y, int w, int h)
    {
        int half = w / 2;

        batcher.box(x + half, y + 1, x + half + 1, y + h - 1, WAVEFORM_PLAYHEAD_COLOR);
    }

    private static String formatWaveformTickLabel(float tick, float playbackSeconds)
    {
        int milliseconds = (int) (tick % 20 == 0 ? 0 : tick % 20 * 5D);

        return tick + "t (" + (int) playbackSeconds + "." + StringUtils.leftPad(String.valueOf(milliseconds), 2, "0") + "s)";
    }

    private static void drawWaveformTimeLabel(Batcher2D batcher, float tick, float playbackSeconds, int x, int y, int w, int h)
    {
        FontRenderer fontRenderer = batcher.getFont();
        String tickLabel = formatWaveformTickLabel(tick, playbackSeconds);

        batcher.textCard(tickLabel, x + w - 8 - fontRenderer.getWidth(tickLabel), y + h / 2 - 4, 0xffffff, 0x99000000);
    }

    private static ActiveAudioAtTick findActiveAudioAtTick(List<AudioClip> clips, float tick)
    {
        int t = (int) tick;

        for (AudioClip clip : clips)
        {
            if (clip.isInside(t))
            {
                SoundBuffer buffer = BBSModClient.getSounds().get(clip.audio.get(), true);

                return new ActiveAudioAtTick(clip, buffer);
            }
        }

        return null;
    }

    private record ActiveAudioAtTick(AudioClip clip, SoundBuffer buffer)
    {}

    private static void drawWaveformPanelChrome(Batcher2D batcher, int x, int y, int w, int h)
    {
        batcher.gradientVBox(x + 2, y + 2, x + w - 2, y + h, 0, Colors.A50);
        batcher.box(x + 1, y, x + 2, y + h, 0xaaffffff);
        batcher.box(x + w - 2, y, x + w - 1, y + h, 0xaaffffff);
        batcher.box(x, y + h - 1, x + w, y + h, 0xffffffff);
    }

    public static void renderAll(Batcher2D batcher, List<AudioClip> clips, float tick, int x, int y, int w, int h, int sw, int sh)
    {
        for (AudioClip clip : clips)
        {
            SoundBuffer audio = BBSModClient.getSounds().get(clip.audio.get(), true);

            if (audio != null && audio.getWaveform() != null && clip.isInside((int) tick))
            {
                renderWaveform(batcher, audio, clip, tick, x, y, w, h, sw, sh);

                y += h + 8;
            }
        }
    }

    public static void renderWaveform(Batcher2D batcher, SoundBuffer audio, AudioClip clip, float tick, int x, int y, int w, int h, int sw, int sh)
    {
        int half = w / 2;

        drawWaveformPanelChrome(batcher, x, y, w, h);

        batcher.clip(x + 2, y + 2, w - 4, h - 4, sw, sh);

        Waveform wave = audio.getWaveform();

        float duration = w / (float) wave.getPixelsPerSecond();
        float playback = TimeUtils.toSeconds(tick - clip.tick.get() + clip.offset.get());
        int offset = (int) (playback * wave.getPixelsPerSecond());
        int waveW = wave.getWidth();

        /* Draw the waveform */
        int runningOffset = waveW - offset;

        if (runningOffset > 0)
        {
            wave.render(batcher, Colors.WHITE, x + half, y, half, h, playback, playback + duration / 2);
        }

        /* Draw the passed waveform */
        if (offset > 0)
        {
            wave.render(batcher, waveformDimColor(), x, y, half, h, playback - duration / 2, playback);
        }

        batcher.unclip(sw, sh);

        drawWaveformPlayhead(batcher, x, y, w, h);

        if (BBSSettings.audioWaveformFilename.get())
        {
            batcher.textCard(audio.getId().toString(), x + 8, y + h / 2 - 4, 0xffffff, 0x99000000);
        }

        if (BBSSettings.audioWaveformTime.get())
        {
            drawWaveformTimeLabel(batcher, tick, playback, x, y, w, h);
        }
    }

    public static boolean renderAudio(File file, List<AudioClip> clips, int totalDuration, int sampleRate, float from, float to)
    {
        float total = totalDuration / 20F;
        Map<AudioClip, Wave> map = new HashMap<>();

        for (AudioClip clip : clips)
        {
            if (!clip.enabled.get())
            {
                continue;
            }

            try
            {
                Wave wave = AudioReader.read(BBSMod.getProvider(), clip.audio.get());

                map.put(clip, wave);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        if (map.isEmpty())
        {
            return false;
        }

        int byteRate = sampleRate * 2;
        int totalBytes = (int) Math.ceil(total * byteRate);
        /* Ensure the buffer size is large enough to hold all
         * audio data and maintain byte alignment. */
        byte[] bytes = new byte[totalBytes + (totalBytes % 2)];
        Wave finalWave = new Wave(1, 1, sampleRate, 16, bytes);
        ByteBuffer buffer = MemoryUtil.memAlloc(2);

        for (AudioClip clip : clips)
        {
            try
            {
                Wave wave = map.get(clip);

                if (wave != null)
                {
                    finalWave.add(buffer, wave,
                        TimeUtils.toSeconds(clip.tick.get()),
                        TimeUtils.toSeconds(clip.offset.get()),
                        TimeUtils.toSeconds(clip.duration.get()),
                        clip.volume.get()
                    );
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        MemoryUtil.memFree(buffer);

        try
        {
            if (from != to && (from >= 0 && to >= 0))
            {
                finalWave = finalWave.excerptMono(from, to);
            }

            WaveWriter.write(file, finalWave);

            return true;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return false;
    }
}