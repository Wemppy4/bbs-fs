package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.graphics.window.NativeFileDrop;
import mchorse.bbs_mod.importers.Importers;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.types.*;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.FFMpegUtils;
import net.minecraft.client.Minecraft;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

/** Imports temporary fixtures through production importers, without opening a menu or changing user assets. */
public final class OriginalImportersProbe {
    public static JsonObject run(JsonObject request) throws Exception {
        JsonObject out=new JsonObject();
        out.addProperty("ok",true);
        out.addProperty("nativeDropAttached",NativeFileDrop.isAttached());
        out.addProperty("importerCount",Importers.getImporters().size());
        if(!request.has("test")||!request.get("test").getAsBoolean())return out;
        Path root=Files.createTempDirectory(Minecraft.getMinecraft().gameDir.toPath(),".aihelper-importers-");
        try {
            File input=Files.createDirectory(root.resolve("input")).toFile();
            File output=root.resolve("output").toFile();
            File png=new File(input,"fixture.png");
            BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<32;y++)for(int x=0;x<32;x++)image.setRGB(x,y,0xff000000|(x*8<<16)|(y*8<<8)|0x5f);
            ImageIO.write(image,"png",png);
            File unicode = new File(input,"Вемпи.png"); Files.copy(png.toPath(),unicode.toPath());
            out.add("nativeDrop",NativeFileDropProbe.run(new String[]{png.getAbsolutePath(),unicode.getAbsolutePath()}));
            PNGImporter copier=new PNGImporter();
            ImporterContext context=new ImporterContext(Collections.singletonList(png),output);
            out.addProperty("recognizesPng",copier.canImport(context));
            out.addProperty("rejectsEmpty",!copier.canImport(new ImporterContext(Collections.emptyList(),output)));
            copier.importFiles(context);
            copier.importFiles(context);
            File[] copies=output.listFiles();
            boolean preserved=copies!=null&&copies.length==2;
            if(copies!=null)for(File copy:copies)preserved&=Arrays.equals(Files.readAllBytes(png.toPath()),Files.readAllBytes(copy.toPath()));
            out.addProperty("copyAndDuplicatePreserved",preserved);

            File skin=new File(input,"legacy.png");
            BufferedImage legacy=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<32;y++)for(int x=0;x<64;x++)legacy.setRGB(x,y,0xff000000|(x*3<<16)|(y*7<<8)|0x6f);
            ImageIO.write(legacy,"png",skin);
            OldSkinImporter skinImporter=new OldSkinImporter();
            context=new ImporterContext(Collections.singletonList(skin),output);
            out.addProperty("recognizesLegacySkin",skinImporter.canImport(context));
            skinImporter.importFiles(context);
            BufferedImage modern=ImageIO.read(new File(output,"legacy.png"));
            out.addProperty("skinExpanded",modern.getWidth()==64&&modern.getHeight()==64);
            out.addProperty("skinOriginalRetained",modern.getRGB(24,18)==legacy.getRGB(24,18));
            out.addProperty("skinLimbMirrored",modern.getRGB(20,52)==legacy.getRGB(7,20));
            out.addProperty("skinOverlayTransparent",modern.getRGB(0,40)==0);

            boolean ffmpeg=FFMpegUtils.checkFFMPEG();
            out.addProperty("ffmpegAvailable",ffmpeg);
            if(ffmpeg) {
                BufferedImage rgb=new BufferedImage(24,16,BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<16;y++)for(int x=0;x<24;x++)rgb.setRGB(x,y,0x50cc99);
                File jpeg=new File(input,"picture.jpg");ImageIO.write(rgb,"jpg",jpeg);
                ToPNGImporter jpegImporter=new ToPNGImporter(UIKeys.IMPORTER_JPEG,".jpg",".jpeg");
                jpegImporter.importFiles(new ImporterContext(Collections.singletonList(jpeg),output));
                BufferedImage converted=ImageIO.read(new File(output,"picture.png"));
                out.addProperty("jpegConverted",converted.getWidth()==24&&converted.getHeight()==16);

                File gif=new File(input,"animation.gif");ImageIO.write(rgb,"gif",gif);
                new GIFImporter().importFiles(new ImporterContext(Collections.singletonList(gif),output));
                File firstFrame=new File(output,"animation_1.png");
                out.addProperty("gifExtracted",firstFrame.isFile()&&ImageIO.read(firstFrame).getWidth()==24);

                byte[] samples=new byte[4096];
                for(int i=0;i<samples.length/4;i++) {
                    short value=(short)(Math.sin(i*Math.PI/20)*10000);
                    samples[i*4]=(byte)value;samples[i*4+1]=(byte)(value>>8);
                    samples[i*4+2]=(byte)value;samples[i*4+3]=(byte)(value>>8);
                }
                AudioFormat format=new AudioFormat(22050,16,2,true,false);
                File wav=new File(input,"stereo.wav");
                try(AudioInputStream stream=new AudioInputStream(new ByteArrayInputStream(samples),format,samples.length/4)) {
                    AudioSystem.write(stream,AudioFileFormat.Type.WAVE,wav);
                }
                new WAVImporter().importFiles(new ImporterContext(Collections.singletonList(wav),output));
                try(AudioInputStream mono=AudioSystem.getAudioInputStream(new ByteArrayInputStream(Files.readAllBytes(new File(output,"stereo.wav").toPath())))) {
                    out.addProperty("wavMono",mono.getFormat().getChannels()==1&&mono.getFrameLength()==1024);
                }
                File aiff=new File(input,"stereo.aiff");
                try(AudioInputStream stream=new AudioInputStream(new ByteArrayInputStream(samples),format,samples.length/4)) {
                    AudioSystem.write(stream,AudioFileFormat.Type.AIFF,aiff);
                }
                File audioOutput=root.resolve("audio").toFile();
                new ToWAVImporter(UIKeys.IMPORTER_AIFF,".aiff").importFiles(new ImporterContext(Collections.singletonList(aiff),audioOutput));
                try(AudioInputStream mono=AudioSystem.getAudioInputStream(new ByteArrayInputStream(Files.readAllBytes(new File(audioOutput,"stereo.wav").toPath())))) {
                    out.addProperty("aiffConverted",mono.getFormat().getChannels()==1&&mono.getFrameLength()==1024);
                }
            }
        } finally {
            try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
                for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
        out.addProperty("temporaryFilesRemoved",!Files.exists(root));
        return out;
    }
}
