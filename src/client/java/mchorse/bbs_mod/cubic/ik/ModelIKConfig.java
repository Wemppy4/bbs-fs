package mchorse.bbs_mod.cubic.ik;

import java.util.List;

record ModelIKConfig(List<Chain> chains)
{
    public record Chain(String controller, String locator, String root)
    {
    }
}
