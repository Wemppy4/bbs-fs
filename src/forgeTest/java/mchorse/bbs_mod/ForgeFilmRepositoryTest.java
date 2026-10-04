package mchorse.bbs_mod;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.FilmManager;
import mchorse.bbs_mod.network.FilmRepositoryOperations;
import mchorse.bbs_mod.utils.repos.RepositoryOperation;
import net.minecraft.init.Bootstrap;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ForgeFilmRepositoryTest
{
    @Rule public TemporaryFolder files = new TemporaryFolder();
    @BeforeClass public static void init() { Bootstrap.register(); }

    private static MapType request(String... values)
    {
        MapType data = new MapType();
        for (int i = 0; i < values.length; i += 2) data.putString(values[i], values[i + 1]);
        return data;
    }
    private static boolean result(FilmManager manager, RepositoryOperation operation, MapType data)
    {
        return FilmRepositoryOperations.apply(manager, operation, data).asNumeric().boolValue();
    }
    @Test public void editsNestedFilmsAndEmptyFoldersThroughTheActualStorage() throws Exception
    {
        FilmManager manager = new FilmManager(() -> files.getRoot());
        assertTrue(result(manager, RepositoryOperation.ADD_FOLDER, request("folder", "scenes/")));
        MapType save = request("id", "scenes/opening");
        save.put("data", manager.create("scenes/opening").toData());
        assertTrue(result(manager, RepositoryOperation.SAVE, save));
        assertTrue(result(manager, RepositoryOperation.SAVE, save));
        assertTrue(FilmRepositoryOperations.apply(manager, RepositoryOperation.BACKUPS, request("id", "scenes/opening")).asList().size() > 0);
        BaseType loaded = FilmRepositoryOperations.apply(manager, RepositoryOperation.LOAD, request("id", "scenes/opening"));
        assertTrue(loaded.isMap());
        assertTrue(result(manager, RepositoryOperation.RENAME, request("from", "scenes/opening", "to", "scenes/renamed")));
        assertFalse(result(manager, RepositoryOperation.DELETE_FOLDER, request("folder", "scenes/")));
        assertTrue(result(manager, RepositoryOperation.DELETE, request("id", "scenes/renamed")));
        assertTrue(result(manager, RepositoryOperation.RENAME_FOLDER, request("from", "scenes/", "to", "empty/")));
        /* Film backups are real contents and prevent deleting the renamed folder. */
        assertFalse(result(manager, RepositoryOperation.DELETE_FOLDER, request("folder", "empty/")));
        assertTrue(result(manager, RepositoryOperation.ADD_FOLDER, request("folder", "disposable/")));
        assertTrue(result(manager, RepositoryOperation.DELETE_FOLDER, request("folder", "disposable/")));
        assertFalse(result(manager, RepositoryOperation.LOAD, request("id", "missing")));
    }
    @Test public void remotePathsCannotReachSiblingDocumentsOrDeleteRepositoryRoot() throws Exception
    {
        File folder = files.newFolder("films");
        FilmManager manager = new FilmManager(() -> folder);
        File outside = files.newFile("outside.dat");
        byte[] original = {42, 24, 7};
        Files.write(outside.toPath(), original);
        for (String path : Arrays.asList("../outside", "nested/../../outside", "/absolute", "C:/outside", "..\\outside", "./", "a/../"))
        {
            try
            {
                FilmRepositoryOperations.apply(manager, RepositoryOperation.DELETE, request("id", path));
                fail("Accepted escaping path: " + path);
            }
            catch (IllegalArgumentException expected) {}
        }
        for (String path : Arrays.asList("", "/", ".", "./", "../"))
        {
            try
            {
                FilmRepositoryOperations.apply(manager, RepositoryOperation.DELETE_FOLDER, request("folder", path));
                fail("Accepted root folder: " + path);
            }
            catch (IllegalArgumentException expected) {}
        }
        assertArrayEquals(original, Files.readAllBytes(outside.toPath()));
        assertTrue(folder.isDirectory());
    }
}
