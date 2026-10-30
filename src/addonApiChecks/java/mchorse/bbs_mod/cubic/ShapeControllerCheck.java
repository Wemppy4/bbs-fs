package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.ShapeController;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.utils.undo.ValueChangeUndo;
import mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import java.util.List;

/** Checks actual persisted values, animation edits and undo, without launching the game. */
public final class ShapeControllerCheck
{
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void near(float a, float b, String why) { check(Math.abs(a - b) < 0.0001F, why + ": " + a + " != " + b); }
    private static ShapeController controller()
    {
        return controller("head", "l");
    }
    private static ShapeController controller(String bone, String side)
    {
        ShapeController c = new ShapeController("0");
        c.bone.set(bone); c.name.set("upper_" + side); c.position.get().set(2, 4, 4.1F);
        for (int i = 0; i < 2; i++)
        {
            var binding = new ShapeController.Binding("" + i);
            binding.shape.set(i == 0 ? "upper_" + side + "_inner" : "upper_" + side + "_outer");
            binding.tilt.set(i == 0 ? 1F : -1F);
            c.bindings.add(binding);
        }
        return c;
    }
    public static void run() throws Exception
    {
        ModelConfig config = new ModelConfig("");
        check(!config.toData().asMap().has("shape_controllers"), "Old models keep empty controller config absent");
        ShapeController c = controller(); config.shapeControllers.add(c);
        ShapeKeys keys = new ShapeKeys(); keys.shapeKeys.put("smile", 0.7F);
        c.set(keys, 0, -0.4F); c.set(keys, 1, 0.2F);
        near(keys.shapeKeys.get("upper_l_inner"), -0.2F, "Height and tilt combine at inner endpoint");
        near(keys.shapeKeys.get("upper_l_outer"), -0.6F, "Height and tilt combine at outer endpoint");
        near(c.read(keys)[0], -0.4F, "Controller reads existing shape pose");
        near(c.read(keys)[1], 0.2F, "Tilt independent of height");
        near(keys.shapeKeys.get("smile"), 0.7F, "Unbound shape survives");
        for (int i = 0; i < 100; i++) c.set(keys, 0, -0.4F);
        near(keys.shapeKeys.get("upper_l_inner"), -0.2F, "No drift on repeated absolute edits");
        c.set(keys, 1, 10);
        near(c.read(keys)[1], 10, "Tilt continues beyond the old limits");
        near(c.read(keys)[0], -0.4F, "Large tilt preserves height");
        c.set(keys, 1, 0.2F);
        near(keys.shapeKeys.get("upper_l_outer"), -0.6F, "Returning from large tilt restores endpoint");
        c.set(keys, 0, 100); near(c.read(keys)[0], 100, "Height has no upper stop");
        c.set(keys, 0, -100); near(c.read(keys)[0], -100, "Height has no lower stop");
        c.set(keys, 0, 0); c.set(keys, 1, 0);
        var data = config.toData();
        ModelConfig restored = new ModelConfig(""); restored.fromData(data);
        check(restored.toData().equals(data), "Config save/load round trip");
        restored.renameBone("head", "face");
        check(restored.shapeControllers.getAllTyped().get(0).bone.get().equals("face"), "Bone rename rewrites controller binding");
        near(restored.shapeControllers.getAllTyped().get(0).position.get().z, 4.1F, "Placement survives round trip");
        restored.fromData(new MapType()); check(restored.shapeControllers.getAllTyped().isEmpty(), "Reload old config clears stale controllers");
        var before = config.shapeControllers.toData();
        BaseValue.edit(config.shapeControllers, list -> list.getAllTyped().clear());
        var undo = new ValueChangeUndo(config.shapeControllers.getPath(), before, config.shapeControllers.toData());
        undo.undo(config); check(config.shapeControllers.getAllTyped().size() == 1, "Delete undo restores controller");
        undo.redo(config); check(config.shapeControllers.getAllTyped().isEmpty(), "Delete redo removes controller");
        previewUndo(c);
        shift();
        mirror();
        modelDiscovery();

        if (Boolean.getBoolean("bbs.check.shapeUI")) withUi(c);
        asset();

        ShapeController one = new ShapeController("0");
        var single = new ShapeController.Binding("0"); single.shape.set("only"); one.bindings.add(single);
        ShapeKeys oneKeys = new ShapeKeys(); one.set(oneKeys, 0, 0.4F);
        near(one.read(oneKeys)[0], 0.4F, "Single-shape height control"); near(one.read(oneKeys)[1], 0, "Unbound tilt is finite");
        System.out.println("ShapeControllerCheck: " + checks + " checks passed");
    }

    private static void withUi(ShapeController c) throws Exception
    {
        /* A hidden input surface and stand-in metrics; no game or render context. */
        if (!org.lwjgl.glfw.GLFW.glfwInit()) throw new IllegalStateException("GLFW input unavailable");
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_VISIBLE, org.lwjgl.glfw.GLFW.GLFW_FALSE);
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_CLIENT_API, org.lwjgl.glfw.GLFW.GLFW_NO_API);
        long handle = org.lwjgl.glfw.GLFW.glfwCreateWindow(16, 16, "Shape controller checks", 0, 0);
        if (handle == 0) throw new IllegalStateException("Hidden input surface unavailable");
        var clientField = net.minecraft.client.MinecraftClient.class.getDeclaredField("instance");
        clientField.setAccessible(true);
        Object oldClient = clientField.get(null);
        var fontField = mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D.class.getDeclaredField("fontRenderer");
        fontField.setAccessible(true);
        Object oldFont = fontField.get(null);
        Object client = mchorse.bbs_mod.utils.UnsafeUtils.getUnsafe().allocateInstance(net.minecraft.client.MinecraftClient.class);
        Object window = mchorse.bbs_mod.utils.UnsafeUtils.getUnsafe().allocateInstance(net.minecraft.client.util.Window.class);
        var handleField = net.minecraft.client.util.Window.class.getDeclaredField("handle");
        handleField.setAccessible(true); handleField.setLong(window, handle);
        var windowField = net.minecraft.client.MinecraftClient.class.getDeclaredField("window");
        windowField.setAccessible(true); windowField.set(client, window);
        clientField.set(null, client);
        fontField.set(null, new mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer()
        {
            @Override public void setRenderer(net.minecraft.client.font.TextRenderer renderer) {}
            @Override public int getWidth(String text) { return text.length() * 6; }
            @Override public int getHeight() { return 7; }
        });
        try { ui(c); }
        finally
        {
            clientField.set(null, oldClient); fontField.set(null, oldFont);
            org.lwjgl.glfw.GLFW.glfwDestroyWindow(handle); org.lwjgl.glfw.GLFW.glfwTerminate();
        }
    }

    /** Gaze: one controller, height and shift on two shapes per eye, both eyes together. */
    private static void shift()
    {
        ShapeController gaze = new ShapeController("0");
        String[][] shapes = {{"pupil_l_y", "1", "0"}, {"pupil_r_y", "1", "0"}, {"pupil_l_x", "0", "1"}, {"pupil_r_x", "0", "1"}};
        for (int i = 0; i < shapes.length; i++)
        {
            var binding = new ShapeController.Binding("" + i);
            binding.shape.set(shapes[i][0]);
            binding.height.set(Float.parseFloat(shapes[i][1]));
            binding.shift.set(Float.parseFloat(shapes[i][2]));
            gaze.bindings.add(binding);
        }
        check(gaze.hasAxis(ShapeController.HEIGHT) && gaze.hasAxis(ShapeController.SHIFT) && !gaze.hasAxis(ShapeController.TILT), "Gaze has height and shift only");
        ShapeKeys keys = new ShapeKeys();
        gaze.set(keys, ShapeController.SHIFT, -0.75F);
        gaze.set(keys, ShapeController.HEIGHT, 0.5F);
        near(keys.shapeKeys.get("pupil_l_x"), -0.75F, "Shift drives the left pupil sideways");
        near(keys.shapeKeys.get("pupil_r_x"), -0.75F, "Shift drives the right pupil the same way");
        near(keys.shapeKeys.get("pupil_r_y"), 0.5F, "Height drives the pupils up");
        float[] read = gaze.read(keys);
        near(read[ShapeController.SHIFT], -0.75F, "Shift reads back");
        near(read[ShapeController.HEIGHT], 0.5F, "Height reads back beside shift");
        near(read[ShapeController.TILT], 0F, "Unbound tilt stays zero");
        gaze.set(keys, ShapeController.SHIFT, 0F);
        near(gaze.read(keys)[ShapeController.HEIGHT], 0.5F, "Shift edit keeps height");

        ShapeController all = controller();
        var side = new ShapeController.Binding("2"); side.shape.set("upper_l_side"); side.shift.set(1F); all.bindings.add(side);
        ShapeKeys three = new ShapeKeys();
        all.set(three, ShapeController.HEIGHT, 0.3F); all.set(three, ShapeController.TILT, -0.2F); all.set(three, ShapeController.SHIFT, 0.6F);
        float[] axes = all.read(three);
        near(axes[ShapeController.HEIGHT], 0.3F, "Three axes: height independent");
        near(axes[ShapeController.TILT], -0.2F, "Three axes: tilt independent");
        near(axes[ShapeController.SHIFT], 0.6F, "Three axes: shift independent");
    }

    private static void mirror()
    {
        ShapeController left = controller("eyelid_lt", "l"), right = controller("eyelid_rt", "r"), lone = controller("pupil", "p");
        List<ShapeController> all = List.of(left, right, lone);
        java.util.Map<String, String> pairs = java.util.Map.of("eyelid_rt", "eyelid_lt");
        check(left.mirror(all, pairs) == right && right.mirror(all, pairs) == left, "Mirror pair is read from either side");
        check(lone.mirror(all, pairs) == null, "Unpaired bone has no mirror controller");
        check(left.mirror(List.of(left), pairs) == null, "Paired bone without a controller has no mirror");
    }

    private static void previewUndo(ShapeController c)
    {
        ModelForm preview = new ModelForm();
        ModelConfig config = new ModelConfig("eyes/1px_polygon");
        config.shapeControllers.add(c);
        var saved = config.toData();
        var history = new mchorse.bbs_mod.utils.undo.UndoManager<mchorse.bbs_mod.settings.values.core.ValueGroup>(10);
        ShapeKeys before = preview.shapeKeys.get().copy();
        c.set(preview.shapeKeys.get(), 0, 0.4F);
        history.pushUndo(new mchorse.bbs_mod.ui.model_editor.ModelShapePreviewUndo(preview, before, preview.shapeKeys.get()));
        before = preview.shapeKeys.get().copy();
        c.set(preview.shapeKeys.get(), 0, 1.4F);
        history.pushUndo(new mchorse.bbs_mod.ui.model_editor.ModelShapePreviewUndo(preview, before, preview.shapeKeys.get()));
        history.markLastUndoNoMerging();
        before = preview.shapeKeys.get().copy();
        c.set(preview.shapeKeys.get(), 1, -0.6F);
        history.pushUndo(new mchorse.bbs_mod.ui.model_editor.ModelShapePreviewUndo(preview, before, preview.shapeKeys.get()));
        history.undo(config);
        near(c.read(preview.shapeKeys.get())[0], 1.4F, "Undo tilt preserves earlier height gesture");
        near(c.read(preview.shapeKeys.get())[1], 0F, "Preview tilt undoes");
        history.undo(config);
        check(preview.shapeKeys.get().shapeKeys.isEmpty(), "All steps of height drag undo together");
        history.redo(config); history.redo(config);
        near(c.read(preview.shapeKeys.get())[0], 1.4F, "Preview height redoes");
        near(c.read(preview.shapeKeys.get())[1], -0.6F, "Preview tilt redoes");
        check(config.toData().equals(saved), "Preview undo never changes persisted model config");
    }

    private static void ui(ShapeController c)
    {
        ModelForm form = new ModelForm();
        var channel = new KeyframeChannel<ShapeKeys>("shape_keys", KeyframeFactories.SHAPE_KEYS);
        ShapeKeys a = new ShapeKeys(), b = new ShapeKeys(); c.set(b, 0, 0.8F);
        channel.insert(0, a); channel.insert(10, b);
        UIKeyframes timeline = new UIKeyframes(null)
        {
            @Override public Float getAutoKeyframeTick() { return 5F; }
            @Override public float getTick() { return 5F; }
        };
        UIKeyframeSheet sheet = new UIKeyframeSheet(0x5599FF, channel, form.shapeKeys).form(form);
        timeline.addSheet(sheet); sheet.selection.add(0);
        UITrackValue<ShapeKeys> track = new UITrackValue<>(sheet, timeline);
        UIShapeControllers controls = new UIShapeControllers(track::edit);
        ModelConfig config = new ModelConfig("");
        config.shapeControllers.add(c);
        controls.fill(null, new ShapeKeys());
        check(controls.selected() == null, "Dashboard warmup without a model has no selected controller");
        check(!controls.height.isEnabled() && !controls.tilt.isEnabled(), "Empty controls disable both axes");
        controls.fill(config, track.getValue());
        timeline.beginValueGesture(); controls.setAxis(1, 0.15F); timeline.endValueGesture();
        check(channel.getKeyframes().size() == 3, "Controller autokeys existing shape track");
        near(c.read(channel.get(1).getValue())[1], 0.15F, "Inserted key has requested tilt");
        near(c.read(channel.get(0).getValue())[1], 0, "Autokey preserves old key");
        check(form.shapeKeys.get().shapeKeys.isEmpty(), "Animation edit does not alter authored pose");
        for (int width : new int[] {160, 220, 320})
        {
            controls.xy(0, 0).w(width).resize();
            check(controls.height.area.w > 0 && controls.height.area.ex() <= width, "Height field fits panel " + width);
            check(controls.tilt.area.w > 0 && controls.tilt.area.ex() <= width, "Tilt field fits panel " + width);
            check(controls.height.area.y == controls.tilt.area.y && controls.height.area.ex() <= controls.tilt.area.x,
                "Both axes share a row without overlap " + width);
        }
        check(controls.height.tooltipImmediate && controls.tilt.tooltipImmediate, "Axis names have immediate tooltips");
        controls.fill(null, new ShapeKeys());
        check(controls.selected() == null, "Closing a populated model clears selection");
        controls.fill(config, track.getValue());
        check(controls.selected() == c, "Reopening a model selects its first controller");
        controls.select(null);
        check(controls.selected() == null && !controls.height.isEnabled(), "Explicit deselection supports immutable controller lists");
    }

    private static void asset() throws Exception
    {
        String path = System.getProperty("bbs.check.shapeAsset");
        if (path == null) return;
        var folder = new java.io.File(path);
        var provider = new mchorse.bbs_mod.resources.AssetProvider();
        provider.register(new mchorse.bbs_mod.resources.packs.ExternalAssetsSourcePack("check", folder.getParentFile()));
        var manager = new mchorse.bbs_mod.cubic.model.ModelManager(provider);
        var link = new mchorse.bbs_mod.resources.Link("check", folder.getName());
        MapType config;
        try (var input = new java.io.FileInputStream(new java.io.File(folder, "config.json"))) { config = CubicLoader.loadFile(input); }
        var model = new mchorse.bbs_mod.cubic.model.loaders.CubicModelLoader().load("eyes/1px_polygon", manager, link, provider.getLinksFromPath(link), config);
        check(model != null, "Actual polygon rig loads");
        var shapes = model.model.getShapeKeys();
        check(shapes.size() == 12 && shapes.stream().allMatch(s -> s.matches("(upper|lower|brow)_[lr]_(inner|outer)")), "Loader exposes twelve English shape names");
        check(model.config.shapeControllers.getAllTyped().size() == 6, "Installed config loads six controls");
        var matrices = new mchorse.bbs_mod.forms.renderers.utils.MatrixCache();
        model.model.resetPose();
        model.captureMatrices(matrices);
        for (var c : model.config.shapeControllers.getAllTyped())
        {
            check(c.bindings.getAllTyped().stream().allMatch(b -> shapes.contains(b.shape.get())), "All control targets exist");
            var position = matrices.get(c.bone.get()).matrix().transformPosition(new org.joml.Vector3f(c.position.get()).div(16));
            near(position.x * 16, c.name.get().endsWith("_l") ? -2 : 2, "Authored controller matches eye X");
            near(position.y * 16, c.name.get().startsWith("brow_") ? 4.25F : c.name.get().startsWith("upper_") ? 4.6F : 2.4F, "Controller matches its geometry after rest pose initialization");
            near(position.z * 16, -4.15F, "Controller lies just in front of eyelids");
        }
        var cubic = (mchorse.bbs_mod.cubic.data.model.Model) model.model;
        for (String side : List.of("l", "r"))
        {
            var brow = cubic.getGroup("eyebrow_" + side);
            check(brow.cubes.isEmpty() && brow.meshes.size() == 1, "Eyebrow is replaced, not doubled");
            var mesh = brow.meshes.get(0);
            check(mesh.baseData.vertices.size() == 12, "Brow has front and back triangles without zero-area sides");
            for (String shape : shapes)
            {
                if (!shape.startsWith("brow_")) continue;
                var target = mesh.data.get(shape);
                check(target != null && target.vertices.size() == mesh.baseData.vertices.size(), "Brow shape topology matches base");
                boolean own = shape.startsWith("brow_" + side + "_");
                float endX = side.equals("l") ? (shape.endsWith("inner") ? -1F : -3F) : (shape.endsWith("inner") ? 1F : 3F);
                for (int i = 0; i < target.vertices.size(); i++)
                {
                    var rest = mesh.baseData.vertices.get(i);
                    var changed = target.vertices.get(i);
                    near(changed.x, rest.x, "Brow width stays fixed");
                    near(changed.z, rest.z, "Brow depth stays fixed");
                    near(changed.y - rest.y, own && Math.abs(rest.x - endX) < 0.0001F ? 1F : 0F, "Only the selected brow endpoint moves");
                    check(target.uvs.get(i).equals(mesh.baseData.uvs.get(i)), "Brow UVs stay unchanged");
                }
            }
        }
        check(!manager.isRelodable(new mchorse.bbs_mod.resources.Link("check", "models/eyes/1px_polygon/shapes/upper_l_inner.obj")), "Shape OBJ is not a standalone model");
    }

    private static void modelDiscovery() throws Exception
    {
        var root = java.nio.file.Files.createTempDirectory("bbs-shape-discovery");
        var rig = root.resolve("models/eyes/1px_polygon");
        var shape = rig.resolve("shapes/upper_l_inner.obj");
        java.nio.file.Files.createDirectories(shape.getParent());
        java.nio.file.Files.writeString(rig.resolve("model.obj"), "");
        java.nio.file.Files.writeString(shape, "");
        var provider = new mchorse.bbs_mod.resources.AssetProvider();
        provider.register(new mchorse.bbs_mod.resources.packs.ExternalAssetsSourcePack("assets", root.toFile()).providesFiles());
        var field = mchorse.bbs_mod.BBSMod.class.getDeclaredField("provider");
        field.setAccessible(true);
        var original = field.get(null);

        try
        {
            field.set(null, provider);
            var manager = new mchorse.bbs_mod.cubic.model.ModelManager(provider);
            check(manager.getAvailableKeys().equals(List.of("eyes/1px_polygon")), "Shape folder does not become a second model");
            var requestedField = manager.getClass().getDeclaredField("requested");
            requestedField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var requested = (java.util.Set<String>) requestedField.get(manager);

            for (var event : mchorse.bbs_mod.utils.watchdog.WatchDogEvent.values())
            {
                requested.add("eyes/1px_polygon");
                manager.accept(shape, event);
                check(!requested.contains("eyes/1px_polygon"), "Shape event invalidates parent: " + event);
            }

            requested.add("eyes/1px_polygon");
            manager.accept(shape.getParent(), mchorse.bbs_mod.utils.watchdog.WatchDogEvent.DELETED);
            check(!requested.contains("eyes/1px_polygon"), "Whole shape folder invalidates parent");
        }
        finally
        {
            field.set(null, original);
            try (var files = java.nio.file.Files.walk(root))
            {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(file);
            }
        }
    }
}
