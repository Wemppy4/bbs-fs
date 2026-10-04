package mchorse.bbs_mod.forge.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;

@IFMLLoadingPlugin.Name("BBSNativeHooks")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"mchorse.bbs_mod.forge.core"})
public final class BBSLoadingPlugin implements IFMLLoadingPlugin
{
    public String[] getASMTransformerClass(){return new String[]{"mchorse.bbs_mod.forge.core.BBSWorldTransformer", "mchorse.bbs_mod.forge.core.BBSMorphTransformer", "mchorse.bbs_mod.forge.core.BBSRenderTransformer"};}
    public String getModContainerClass(){return null;}
    public String getSetupClass(){return null;}
    public void injectData(Map<String,Object> data){}
    public String getAccessTransformerClass(){return null;}
}
