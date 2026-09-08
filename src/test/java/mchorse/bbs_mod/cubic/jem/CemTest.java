package mchorse.bbs_mod.cubic.jem;

import com.google.gson.JsonParser;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.math.Variable;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Standalone manual sanity test for the CEM expression engine ({@link CemParser}), the instance
 * clock ({@link CemState}) and the bone mapping of a parsed model ({@link JemModelParser} +
 * {@link CemAnimation}).
 *
 * <p>It needs no Minecraft — run {@link #main(String[])} from your IDE, or after a build:
 * {@code java -cp "build/classes/java/main;build/classes/java/test;<gson jar>" mchorse.bbs_mod.cubic.jem.CemTest}
 * (Gson and JOML are the only libraries the model checks pull in; the client's class path has both).
 * It lives in the test source set so it never ships in the jar. Add value checks with {@link #check},
 * parse-only checks with {@link #parses}, and set variables (as the animation runtime will each frame)
 * with {@code parser.setValue(name, value)}.</p>
 */
public class CemTest
{
    private static final String SHARED_HEADER = System.lineSeparator() + "--- entity variables are shared between models (CemVariables) ---";

    private static final CemParser PARSER = new CemParser();
    private static int fails = 0;

    public static void main(String[] args)
    {
        PARSER.setValue("limb_swing", 0);
        PARSER.setValue("part.rx", -100);
        PARSER.setValue("leg4:Hoof.rx", 120);

        System.out.println("--- value checks ---");
        check("3 * sin(limb_swing / 4) - 2", -2);
        check("clamp(-0.5 * part.rx, 0, 90)", 50);
        check("if(leg4:Hoof.rx > 90, leg4:Hoof.rx - 90, 0)", 30);
        check("torad(180)", Math.PI);
        check("todeg(pi)", 180);
        check("lerp(0.5, 10, 20)", 15);
        check("fmod(-1, 3)", 2);
        check("in(2, 0, 1, 2)", 1);
        check("between(0.5, 0, 1)", 1);
        check("abs(-4) + max(1,2,3) + min(5,2)", 9);
        check("signum(-3.2)", -1);
        check("frac(2.75)", 0.75);
        check("equals(1.0, 1.00001, 0.001)", 1);
        check("!false && true", 1);
        check("2 ^ 10", 1024);
        check("wraprad(pi*3)", Math.PI);

        System.out.println("\n--- nbt() is cut out whole, parentheses balanced ---");
        check("1 + nbt(SelectedItem.id, raw:iregex:.*(shield).*) * 5", 1);
        check("nbt(a, (b, (c))) + 2", 2);
        check("if(nbt(x), 7, 3)", 3);
        check("nbt(unterminated, (oops", 0);

        System.out.println("\n--- real Fresh Animations expressions (must parse, finite) ---");
        parses("if(varb.21_2_plus,var.root_ty-(root.sy-1)*24,0)");
        parses("torad( +12*sin( var.headpitch_drag/180*pi ) +sin(var.Bt) )*var.idle");
        parses("clamp( if( varb.fcc, var.root_angle, is_swimming || is_gliding, var.root_angle +1.6 *frame_time, if( var.gliding>0, 0, var.root_angle -1.6 *frame_time ) ), 0, 1 )");
        parses("is_swinging_right_arm && is_using_item &&( is_blocking || ( is_right_handed&&nbt(SelectedItem.id, raw:iregex:.*shield.*)) )");
        parses("var.r +age/(11.5-2*random(id))");

        System.out.println("\n--- instance clock (CemState.advance) ---");
        CemVariables variables = new CemVariables();
        CemState state = new CemState(variables);
        List<Variable> slots = Collections.singletonList(new Variable("var.drag", 0));

        put(state, slots, 7);
        clock("first sight", state.advance(10.0), 0);
        clock("one tick forward", state.advance(11.0), 0.05);
        clock("same frame again", state.advance(11.0), 0);
        clock("same tick, earlier partial (a resample)", state.advance(10.5), 0);
        flag("resample keeps the state", get(state, slots) == 7 && state.frameCounter == 2);
        clock("next tick, stepped from the real last frame", state.advance(12.0), 0.05);
        clock("scrub back", state.advance(5.0), 0);
        flag("scrub back re-seeds", get(state, slots) == 0 && state.frameCounter == 1);
        put(state, slots, 3);
        clock("jump forward past the catch-up limit", state.advance(20.0), 0);
        flag("jump re-seeds", get(state, slots) == 0 && state.frameCounter == 1);
        clock("quarter tick", state.advance(20.25), 0.0125);
        clock("a whole catch-up window is still stepped", state.advance(24.25), 0.2);

        System.out.println(SHARED_HEADER);

        CemVariables entity = new CemVariables();
        CemState bodyClock = new CemState(entity);
        CemState capeClock = new CemState(entity);

        /* Two programs, each with its own Variable object for the same name: a body publishing a value
         * and a cape of the same entity reading it, which is all Fresh Moves' cape has to go on. */
        List<Variable> body = Collections.singletonList(new Variable("var.player_body_rx", 0));
        List<Variable> cape = Collections.singletonList(new Variable("var.player_body_rx", 0));

        put(bodyClock, body, 0.42);
        capeClock.load(cape);
        flag("a model reads what another model of the entity wrote", cape.get(0).doubleValue() == 0.42);

        CemState alone = new CemState(new CemVariables());
        List<Variable> stranger = Collections.singletonList(new Variable("var.player_body_rx", 0));

        alone.load(stranger);
        flag("a store of its own stays empty", stranger.get(0).doubleValue() == 0);

        bodyClock.advance(5.0);
        bodyClock.advance(1.0);
        capeClock.load(cape);
        flag("a re-seed clears the whole entity, cape included", cape.get(0).doubleValue() == 0);

        System.out.println("\n--- a part's direct submodel stays direct once the vanilla rig hangs the part on another ---");

        /* Fresh Animations' allay, boiled down: body holds the geometry, head2 is its direct submodel,
         * and the vanilla rig hangs body on root. head2's -6.1 is written for the direct mapping; read
         * as a deeper submodel it would take body's pivot on top and land six pixels too high - which is
         * exactly what the game showed. */
        JemModelParser.Result allay = JemModelParser.parse(JsonParser.parseString("{\"textureSize\":[32,32],\"models\":["
            + "{\"part\":\"root\",\"id\":\"root\"},"
            + "{\"part\":\"body\",\"id\":\"body\",\"translate\":[0,-6,0],"
            + "\"boxes\":[{\"coordinates\":[-1.5,2,-1,3,4,2],\"textureOffset\":[0,0]}],"
            + "\"submodels\":[{\"id\":\"head2\",\"translate\":[0,6,0],\"boxes\":[{\"coordinates\":[-2.5,6,-2.5,5,5,5],\"textureOffset\":[0,0]}]}],"
            + "\"animations\":[{\"body.ty\":\"-4\",\"head2.ty\":\"-6.1\"}]}]}").getAsJsonObject(),
            null, null, new CemHierarchy(Map.of("body", "root"), Map.of()));
        CemAnimation program = allay.animation();
        ModelGroup allayBody = allay.model().getGroup("body");
        ModelGroup allayHead = allay.model().getGroup("head2");

        program.apply(program.createState(), null, 0F);
        flag("the rig hung body on root", allayBody.parent != null && allayBody.parent.id.equals("root"));
        flag("body.ty = -4 is parent-relative: y = 4", Math.abs(allayBody.current.translate.y - 4) < 1e-4);
        flag("head2.ty = -6.1 stays direct: y = 6.1, not body's pivot on top", Math.abs(allayHead.current.translate.y - 6.1) < 1e-4);

        System.out.println("\n--- a part written invisible takes its submodels with it; visible_boxes hides its own boxes alone ---");

        /* Fresh Animations' evoker hides "arms" while casting and shows the separate arms instead;
         * the crossed arms live in a submodel of "arms", so a flag that stopped at the part left a
         * second pair on screen. */
        JemModelParser.Result evoker = JemModelParser.parse(JsonParser.parseString("{\"textureSize\":[64,64],\"models\":["
            + "{\"part\":\"arms\",\"id\":\"arms\",\"translate\":[0,-24,0],"
            + "\"boxes\":[{\"coordinates\":[-4,16,-2,8,4,4],\"textureOffset\":[0,0]}],"
            + "\"submodels\":[{\"id\":\"arms_rotation\",\"translate\":[0,22,0],\"boxes\":[{\"coordinates\":[-4,18,-2,8,4,4],\"textureOffset\":[0,0]}]}],"
            + "\"animations\":[{\"arms.visible\":\"hide == 0\",\"arms.visible_boxes\":\"boxes\"}]}]}").getAsJsonObject(), null, null);
        CemAnimation hider = evoker.animation();
        CemState hiderState = hider.createState();
        ModelGroup arms = evoker.model().getGroup("arms");
        ModelGroup crossed = evoker.model().getGroup("arms_rotation");

        hider.parser.setValue("boxes", 1);
        hider.parser.setValue("hide", 1);
        hider.apply(hiderState, null, 0F);
        flag("arms hidden: arms and the crossed pair under it are both gone", !arms.visible && !crossed.visible);
        hider.parser.setValue("hide", 0);
        hider.apply(hiderState, null, 0F);
        flag("arms shown again: both are back", arms.visible && crossed.visible);
        hider.parser.setValue("boxes", 0);
        hider.apply(hiderState, null, 0F);
        flag("visible_boxes off: arms' own boxes hidden, the pair under it still drawn", !arms.visible && crossed.visible);

        System.out.println("\n--- an empty part takes vanilla's rotation point; a part with geometry keeps its file's ---");

        /* Fresh Animations' villager: head is an empty shell at a zero translate, animated about the
         * neck; the face lives in headwear, placed by the file. Vanilla says the head turns at (0, 24, 0)
         * and, for the test's sake, that headwear sits somewhere else - which a drawn part must ignore. */
        JemModelParser.Result villager = JemModelParser.parse(JsonParser.parseString("{\"textureSize\":[64,64],\"models\":["
            + "{\"part\":\"head\",\"id\":\"head\",\"translate\":[0,0,0],\"animations\":[{\"head.rx\":\"head.rx\"}]},"
            + "{\"part\":\"headwear\",\"id\":\"headwear\",\"translate\":[0,-24,0],"
            + "\"boxes\":[{\"coordinates\":[-4,24,-4,8,8,8],\"textureOffset\":[0,0]}]}]}").getAsJsonObject(),
            null, null, new CemHierarchy(Map.of("headwear", "head"), Map.of("head", new Vector3f(0F, 24F, 0F), "headwear", new Vector3f(0F, 10F, 0F))));
        ModelGroup villagerHead = villager.model().getGroup("head");
        ModelGroup villagerHat = villager.model().getGroup("headwear");

        flag("the empty head turns at the neck vanilla gave it", villagerHead.initial.translate.y == 24F);
        flag("the drawn headwear keeps the pivot its file gave it", villagerHat.initial.translate.y == 24F && villagerHat.parent == villagerHead);

        System.out.println("\n--- an empty part hung on the file's parent rests at vanilla's offset from it, where the vanilla frame puts it too ---");

        /* Fresh Animations' guardian: the spikes are empty shells vanilla hangs on the head, which the
         * pack calls body and rotates about a point of its own (8 up, not vanilla's 24), and the pack's
         * own spike geometry is placed from spine1.ty. Rest and vanilla frame must agree, or the spikes
         * jump sixteen pixels whenever the frame is missing. */
        JemModelParser.Result guardian = JemModelParser.parse(JsonParser.parseString("{\"textureSize\":[64,32],\"models\":["
            + "{\"part\":\"body\",\"id\":\"body\",\"translate\":[0,-8,0],\"boxes\":[{\"coordinates\":[-6,2,-8,12,12,16],\"textureOffset\":[0,0]}],"
            + "\"submodels\":[{\"id\":\"spine_1\",\"translate\":[0,12.5,7],\"boxes\":[{\"coordinates\":[-1,12.5,7,2,4,2],\"textureOffset\":[0,0]}]}],"
            + "\"animations\":[{\"spine_1.ty\":\"-15.5 + spine1.ty\"}]},"
            + "{\"part\":\"spine1\",\"id\":\"spine1\"}]}").getAsJsonObject(), null, null,
            new CemHierarchy(Map.of("spine1", "body"), Map.of("spine1", new Vector3f(0F, 16.08F, 8.08F)), Map.of("spine1", new Vector3f(0F, -7.92F, 8.08F))));
        ModelGroup spike = guardian.model().getGroup("spine1");
        ModelGroup faSpike = guardian.model().getGroup("spine_1");
        CemAnimation guardianProgram = guardian.animation();

        flag("the empty spike rests at the pack's head pivot plus vanilla's offset: y = 8 - 7.92", Math.abs(spike.initial.translate.y - 0.08F) < 1e-3);
        guardian.model().resetPose();
        guardianProgram.apply(guardianProgram.createState(), null, 0F, false, null, null);
        float atRest = faSpike.current.translate.y;
        CemVanillaSeed guardianFrame = new CemVanillaSeed();
        CemVanillaSeed.Part vanillaSpike = guardianFrame.part("spine1");
        vanillaSpike.ty = 7.92F;
        vanillaSpike.tz = 8.08F;
        vanillaSpike.sx = vanillaSpike.sy = vanillaSpike.sz = 1F;
        vanillaSpike.visible = true;
        guardian.model().resetPose();
        guardianProgram.apply(guardianProgram.createState(), null, 0F, false, null, guardianFrame);
        flag("the pack's spike lands in the same place with and without the vanilla frame (-15.5 + 7.92, direct: y = 7.58)", Math.abs(atRest - 7.58F) < 1e-2 && Math.abs(faSpike.current.translate.y - atRest) < 1e-3);

        System.out.println("\n--- the vanilla frame seeds the program: its angle and flag outright, its position where vanilla moved the part ---");

        CemVanillaSeed frame = new CemVanillaSeed();
        CemVanillaSeed.Part seededHead = frame.part("head");
        CemAnimation seeded = villager.animation();

        seededHead.rx = 0.5F;
        seededHead.visible = false;
        seededHead.sx = seededHead.sy = seededHead.sz = 1F;
        seeded.apply(seeded.createState(), null, 0F, false, null, frame);
        flag("the head turns by vanilla's angle", Math.abs(villagerHead.current.rotate.x + Math.toDegrees(0.5)) < 1e-3);
        flag("vanilla hid the head, and the headwear under it went with it", !villagerHead.visible && !villagerHat.visible);
        flag("a part vanilla did not move keeps its rest position", villagerHead.current.translate.y == 24F);

        seededHead.visible = true;
        seededHead.moved = true;
        seededHead.ty = seededHead.ay = 4F;
        seeded.apply(seeded.createState(), null, 0F, false, null, frame);
        flag("a part vanilla moved four down from the top lands at y = 20", Math.abs(villagerHead.current.translate.y - 20F) < 1e-4 && villagerHead.visible);

        System.out.println("\n--- a layer is folded into its base as a material ---");

        /* A sheep and its wool: the same bones, other boxes. The wool also brings a tuft the sheep has
         * not, and animates it - which the fold cannot honour, and says. */
        JemModelParser.Result sheep = JemModelParser.parse(JsonParser.parseString("{\"textureSize\":[64,32],\"models\":["
            + "{\"part\":\"body\",\"id\":\"body\",\"translate\":[0,-19,0],\"boxes\":[{\"coordinates\":[-4,13,-8,8,16,6],\"textureOffset\":[28,8]}],"
            + "\"submodels\":[{\"id\":\"head2\",\"translate\":[0,18,-8],\"boxes\":[{\"coordinates\":[-3,16,-14,6,6,8],\"textureOffset\":[0,0]}]}]}]}").getAsJsonObject(), null, null);
        JemModelParser.Result wool = JemModelParser.parse(JsonParser.parseString("{\"textureSize\":[64,32],\"models\":["
            + "{\"part\":\"body\",\"id\":\"body\",\"translate\":[0,-19,0],\"boxes\":[{\"coordinates\":[-4,13,-8,8,16,6],\"textureOffset\":[28,8],\"sizeAdd\":1.75}],"
            + "\"submodels\":[{\"id\":\"head2\",\"translate\":[0,18,-8],\"boxes\":[{\"coordinates\":[-3,16,-14,6,6,8],\"textureOffset\":[0,0],\"sizeAdd\":0.6}]},"
            + "{\"id\":\"tuft\",\"translate\":[0,29,-6],\"boxes\":[{\"coordinates\":[-1,29,-7,2,2,2],\"textureOffset\":[0,0]}]}],"
            + "\"animations\":[{\"tuft.rx\":\"sin(age)\"}]}]}").getAsJsonObject(), null, null);
        java.util.Collection<String> folded = JemModelParser.graft(sheep, wool, "wool");
        ModelGroup sheepBody = sheep.model().getGroup("body");
        ModelGroup sheepHead = sheep.model().getGroup("head2");
        ModelGroup tuft = sheep.model().getGroup("tuft");

        flag("the wool's boxes sit on the sheep's bones, under the wool material", sheepBody.cubes.size() == 2 && sheepBody.cubes.get(1).material.equals("wool") && sheepHead.cubes.size() == 2 && sheepHead.cubes.get(1).material.equals("wool"));
        flag("the sheep's own boxes keep the default material", sheepBody.cubes.get(0).material.isEmpty());
        flag("the tuft only the wool has came along under the body, with its pivot", tuft != null && tuft.parent == sheepBody && tuft.initial.translate.y == 29F && tuft.cubes.get(0).material.equals("wool"));
        flag("the tuft's animation is left out, and said so", folded.size() == 1 && folded.iterator().next().contains("tuft"));

        System.out.println("\n--- the entity behind a file name (CemNames) ---");
        flag("cold_cow_baby is a cow", CemNames.entity("cold_cow_baby").equals("cow"));
        flag("drowned_outer is a drowned", CemNames.entity("drowned_outer").equals("drowned"));
        flag("pig_baby_saddle is a pig", CemNames.entity("pig_baby_saddle").equals("pig"));
        flag("sheep_wool_undercoat is a sheep", CemNames.entity("sheep_wool_undercoat").equals("sheep"));
        flag("villager_baby2 is a villager", CemNames.entity("villager_baby2").equals("villager"));
        flag("zombie_horse_baby_armor is a zombie_horse", CemNames.entity("zombie_horse_baby_armor").equals("zombie_horse"));
        flag("puffer_fish_big keeps its size", CemNames.entity("puffer_fish_big").equals("puffer_fish_big"));
        flag("player_slim stays itself", CemNames.entity("player_slim").equals("player_slim"));
        flag("head_creeper is a skull, not a creeper", CemNames.entity("head_creeper").equals("head_creeper"));
        flag("sheep_wool is the sheep's wool material", CemNames.layer("sheep_wool").equals(new CemNames.Layer("sheep", "wool")));
        flag("sheep_wool_undercoat is the sheep's wool_undercoat", CemNames.layer("sheep_wool_undercoat").equals(new CemNames.Layer("sheep", "wool_undercoat")));
        flag("pig_baby_saddle is the saddle of the young pig", CemNames.layer("pig_baby_saddle").equals(new CemNames.Layer("pig_baby", "saddle")));
        flag("creeper_charge stays a model of its own", CemNames.layer("creeper_charge") == null && CemNames.layer("cow") == null);

        System.out.println("\n--- part names, OptiFine to vanilla and back (CemPartNames) ---");
        flag("horse: neck is head_parts", CemPartNames.of("horse").vanilla("neck").equals("head_parts"));
        flag("horse: left_hind_leg reads back as back_left_leg", CemPartNames.of("horse").optifine("left_hind_leg").equals("back_left_leg"));
        flag("cow: leg1 is right_hind_leg", CemPartNames.of("cow").vanilla("leg1").equals("right_hind_leg"));
        flag("blaze: stick12 is part11", CemPartNames.of("blaze").vanilla("stick12").equals("part11"));
        flag("magma_cube: core is inside_cube, segment1 is cube0", CemPartNames.of("magma_cube").vanilla("core").equals("inside_cube") && CemPartNames.of("magma_cube").vanilla("segment1").equals("cube0"));
        flag("bee: body is bone and torso is body", CemPartNames.of("bee").vanilla("body").equals("bone") && CemPartNames.of("bee").vanilla("torso").equals("body"));
        flag("bee: vanilla body reads back as torso", CemPartNames.of("bee").optifine("body").equals("torso"));
        flag("guardian: spine12 is spike11, body is head", CemPartNames.of("guardian").vanilla("spine12").equals("spike11") && CemPartNames.of("guardian").vanilla("body").equals("head"));
        flag("a name nobody mapped is itself", CemPartNames.of("frog").vanilla("croaking_body").equals("croaking_body") && CemPartNames.of("nothing").optifine("x").equals("x"));

        System.out.println(fails == 0 ? "\n=== ALL PASS ===" : "\n=== " + fails + " FAILED ===");
    }

    /** Write a value into the state's store, the way a frame's statements leave one behind. */
    private static void put(CemState state, List<Variable> slots, double value)
    {
        slots.get(0).set(value);
        state.store(slots);
    }

    private static double get(CemState state, List<Variable> slots)
    {
        state.load(slots);

        return slots.get(0).doubleValue();
    }


    private static void check(String expr, double expected)
    {
        double value = PARSER.parseExpression(expr).get().doubleValue();
        boolean ok = Math.abs(value - expected) < 1e-4;

        if (!ok)
        {
            fails += 1;
        }

        System.out.printf("%-50s = %-11.5f (want %-9.5f) %s%n", expr, value, expected, ok ? "OK" : "FAIL");
    }

    private static void parses(String expr)
    {
        double value = PARSER.parseExpression(expr).get().doubleValue();
        boolean ok = !Double.isNaN(value) && !Double.isInfinite(value);

        if (!ok)
        {
            fails += 1;
        }

        System.out.printf("%s  %.4f  %s%n", ok ? "OK  " : "FAIL", value, expr.length() > 60 ? expr.substring(0, 60) + "..." : expr);
    }

    private static void clock(String label, double frameTime, double expected)
    {
        flag(String.format("%s: frame_time %.4f (want %.4f)", label, frameTime, expected), Math.abs(frameTime - expected) < 1e-9);
    }

    private static void flag(String label, boolean ok)
    {
        if (!ok)
        {
            fails += 1;
        }

        System.out.printf("%s  %s%n", ok ? "OK  " : "FAIL", label);
    }
}
