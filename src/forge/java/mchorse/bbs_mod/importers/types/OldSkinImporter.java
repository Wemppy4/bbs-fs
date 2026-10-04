package mchorse.bbs_mod.importers.types;

import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.ImporterUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.Operation;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.PNGEncoder;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.joml.Vector2i;

import java.io.File;
import java.io.FileInputStream;

public class OldSkinImporter implements IImporter
{
    public static void convertSkin(File in, File out) throws Exception
    {
        try (FileInputStream stream = new FileInputStream(in))
        {
            Pixels source = Pixels.fromPNGStream(stream);
            float s = source.width / 64F;
            Pixels destination = Pixels.fromSize((int) (64F * s), (int) (64F * s));

            /* Copy the entire skin */
            drawImage(source, destination, 0, 0, 64, 32, 0, 0, 64, 32, s);

            /* Arm */
            drawImage(source, destination, 24, 48, 20, 52, 4, 16, 8, 20, s);
            drawImage(source, destination, 28, 48, 24, 52, 8, 16, 12, 20, s);
            drawImage(source, destination, 20, 52, 16, 64, 8, 20, 12, 32, s);
            drawImage(source, destination, 24, 52, 20, 64, 4, 20, 8, 32, s);
            drawImage(source, destination, 28, 52, 24, 64, 0, 20, 4, 32, s);
            drawImage(source, destination, 32, 52, 28, 64, 12, 20, 16, 32, s);

            /* Leg */
            drawImage(source, destination, 40, 48, 36, 52, 44, 16, 48, 20, s);
            drawImage(source, destination, 44, 48, 40, 52, 48, 16, 52, 20, s);
            drawImage(source, destination, 36, 52, 32, 64, 48, 20, 52, 32, s);
            drawImage(source, destination, 40, 52, 36, 64, 44, 20, 48, 32, s);
            drawImage(source, destination, 44, 52, 40, 64, 40, 20, 44, 32, s);
            drawImage(source, destination, 48, 52, 44, 64, 52, 20, 56, 32, s);

            source.delete();

            PNGEncoder.writeToFile(destination, out);

            destination.delete();
        }
    }

    private static void drawImage(Pixels source, Pixels destination, float dx1, float dy1, float dx2, float dy2, float sx1, float sy1, float sx2, float sy2, float scale)
    {
        destination.drawPixels(source,
            (int) (dx1 * scale), (int) (dy1 * scale), (int) (dx2 * scale), (int) (dy2 * scale),
            (int) (sx1 * scale), (int) (sy1 * scale), (int) (sx2 * scale), (int) (sy2 * scale)
        );
    }

    @Override
    public IKey getName()
    {
        return UIKeys.IMPORTER_OLD_SKIN;
    }

    @Override
    public boolean canImport(ImporterContext context)
    {
        if (context.files.isEmpty()) return false;
        for (File file : context.files)
        {
            if (!isOldSkin(file)) return false;
        }

        return true;
    }

    private boolean isOldSkin(File file)
    {
        Vector2i vector2i = PNGEncoder.readSize(file);

        if (vector2i == null)
        {
            return false;
        }

        return Operation.equals(Math.abs(vector2i.x / (float) vector2i.y), 2D);
    }

    @Override
    public void importFiles(ImporterContext context)
    {
        for (File file : context.files)
        {
            try
            {
                File destination = context.getDestination(this);

                convertSkin(file, new File(destination, ImporterUtils.getName(destination, file.getName())));
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Could not convert skin " + file, e);
            }
        }
    }
}