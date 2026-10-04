package mchorse.bbs_mod;

import mchorse.bbs_mod.cubic.*;
import mchorse.bbs_mod.cubic.data.model.*;
import mchorse.bbs_mod.cubic.data.animation.*;
import mchorse.bbs_mod.data.*;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.resources.*;
import mchorse.bbs_mod.resources.packs.*;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoop;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ForgeFoundationTest
{
    @org.junit.BeforeClass public static void initFactories() {
        mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories.setup();
    }
    @Test public void originalSteveGeometryAndHierarchyLoad() throws Exception {
        MolangParser parser=new MolangParser(); MolangHelper.registerVars(parser);
        try(InputStream stream=getClass().getResourceAsStream("/assets/bbs/assets/models/player/steve/steve.bbs.json")) {
            CubicLoader.LoadingInfo loaded=new CubicLoader().load(parser,stream,"bundled Steve");
            assertNotNull(loaded.model);assertEquals(37,loaded.model.getAllGroups().size());
            assertNotNull(loaded.model.getGroup("head").parent);
            int vertices=0;
            for(ModelGroup group:loaded.model.getAllGroups()) for(ModelCube cube:group.cubes) for(ModelQuad quad:cube.quads) {
                assertEquals(4,quad.vertices.size());
                for(ModelVertex vertex:quad.vertices) { assertTrue(Float.isFinite(vertex.vertex.x));assertTrue(Float.isFinite(vertex.uv.y));vertices++; }
            }
            assertTrue(vertices>100);
        }
    }
    @Test public void existingDroneMolangAndKeyframesMoveBones() throws Exception {
        MolangParser parser=new MolangParser(); MolangHelper.registerVars(parser);
        try(InputStream stream=getClass().getResourceAsStream("/fixtures/helper_drone/model.bbs.json")) {
            CubicLoader.LoadingInfo loaded=new CubicLoader().load(parser,stream,"helper_drone");
            assertEquals(6,loaded.animations.getAll().size());
            Animation fold=loaded.animations.get("fold");
            loaded.model.resetPose();loaded.model.apply(null,fold,0,1,0,false);
            java.util.Map<String,org.joml.Vector3f> before=new java.util.HashMap<>();
            for(ModelGroup g:loaded.model.getAllGroups()) before.put(g.id,new org.joml.Vector3f(g.current.rotate));
            loaded.model.resetPose();loaded.model.apply(null,fold,10,1,0,false);
            boolean changed=false;
            for(ModelGroup g:loaded.model.getAllGroups()) changed|=g.current.rotate.distance(before.get(g.id))>0.1F;
            assertTrue("Actual embedded fold animation must move the rig",changed);
        }
    }
    @Test public void modelFormRoundTripKeepsPoseAndPlayback() throws Exception {
        ModelForm form=new ModelForm();form.model.set("helper_drone");
        mchorse.bbs_mod.cubic.animation.ActionConfig action=new mchorse.bbs_mod.cubic.animation.ActionConfig("grab");
        action.speed=0;action.tick=12;form.actions.get().actions.put("idle",action);
        form.pose.get().getOrCreate("antenna").rotate.set(0.2F,0.3F,0.4F);form.transform.get().scale.set(2);
        ModelForm restored=new ModelForm();restored.fromData(DataToString.mapFromString(DataToString.toString(form.toData())));
        assertEquals(form.toData(),restored.toData());
    }
    @Test public void java8LoopRetainsRecordValueSemantics() {
        KeyframeLoop a=new KeyframeLoop("loop",0,10,30),b=KeyframeLoop.fromData(a.toData());
        assertEquals(a,b);assertEquals(a.hashCode(),b.hashCode());assertEquals(10,a.sourceTick(20),0);assertEquals(5,a.sourceTick(25),0);
    }
    @Test public void globalAssetWinsAndWorldAddsMissingFiles() throws Exception {
        File temp=Files.createTempDirectory("bbs-assets").toFile();File global=new File(temp,"global"),world=new File(temp,"world");global.mkdirs();world.mkdirs();
        try {
            Files.write(new File(global,"same.txt").toPath(),"global".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(world,"same.txt").toPath(),"world".getBytes(StandardCharsets.UTF_8));
            Files.write(new File(world,"extra.txt").toPath(),"extra".getBytes(StandardCharsets.UTF_8));
            DynamicSourcePack pack = new DynamicSourcePack(new ExternalAssetsSourcePack("assets",global).providesFiles());
            pack.setSecondary(new ExternalAssetsSourcePack("assets",world).providesFiles());
            AssetProvider provider=new AssetProvider();provider.register(pack);
            try(InputStream stream=provider.getAsset(Link.assets("same.txt"))) {assertEquals('g',stream.read());}
            assertTrue(provider.hasAsset(Link.assets("extra.txt")));
            assertEquals(new File(global,"same.txt"),provider.getFile(Link.assets("same.txt")));
            assertEquals(new File(world,"extra.txt"),provider.getFile(Link.assets("extra.txt")));
            assertEquals(new File(global,"new.txt"),provider.getFile(Link.assets("new.txt")));
            pack.setSecondary(null);
            assertFalse(provider.hasAsset(Link.assets("extra.txt")));
        } finally {
            new File(global,"same.txt").delete();new File(world,"same.txt").delete();new File(world,"extra.txt").delete();global.delete();world.delete();temp.delete();
        }
    }
}
