package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.ui.ContentType;

import java.util.Collection;
import java.util.function.Consumer;

public class UIDataUtils
{
    public static void requestNames(ContentType type, Consumer<Collection<String>> consumer)
    {
        type.getRepository().requestKeys(consumer);
    }
}
