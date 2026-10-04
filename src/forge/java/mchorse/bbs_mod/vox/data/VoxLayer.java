package mchorse.bbs_mod.vox.data;

import mchorse.bbs_mod.vox.VoxReader;

import java.io.InputStream;

public class VoxLayer extends VoxBaseNode
{
    public VoxLayer(InputStream stream, VoxReader reader) throws Exception
    {
        this.id = reader.readInt(stream);
        this.attrs = reader.readDictionary(stream);
        this.num = reader.readInt(stream);
    }

    public boolean isHidden()
    {
        return this.attrs.containsKey("_hidden") && this.attrs.get("_hidden").equals("1");
    }
}