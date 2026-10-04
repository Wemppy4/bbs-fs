package mchorse.bbs_mod.particles.vanilla;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import java.util.HashMap;
import java.util.Map;

/** Static native sprite samples. This never constructs, spawns, or ticks world particles. */
public final class VanillaParticlePreview
{
    private static final ResourceLocation PARTICLES = new ResourceLocation("textures/particle/particles.png");
    private static final Map<ResourceLocation, EnumParticleTypes> TYPES = new HashMap<>();

    public static void clearCache() { TYPES.clear(); }

    public static void render(UIContext context, ResourceLocation id, float x, float y, float size, int fallbackColor)
    {
        if (size <= 0) return;
        if (!TYPES.containsKey(id))
        {
            EnumParticleTypes match = null;
            for (EnumParticleTypes type : EnumParticleTypes.values())
            {
                if (new ResourceLocation(type.getParticleName()).equals(id)) { match = type; break; }
            }
            TYPES.put(id, match);
        }
        EnumParticleTypes type = TYPES.get(id);
        int index = -1, color = 0xFFFFFFFF;
        String sprite = null;
        ResourceLocation texture = PARTICLES;
        float u1 = 0, v1 = 0, u2 = 1, v2 = 1;
        if (type != null)
        {
            /* Texture indices and frame strips from the native Particle* constructors/update methods. */
            switch (type)
            {
                case EXPLOSION_NORMAL: case CLOUD: case SNOW_SHOVEL: index = 4; break;
                case SMOKE_NORMAL: case SMOKE_LARGE: index = 4; color = 0xFF888888; break;
                case REDSTONE: index = 4; color = 0xFFFF3030; break;
                case FALLING_DUST: index = 4; color = 0xFFDBCAA0; break;
                case FIREWORKS_SPARK: index = 164; break;
                case WATER_BUBBLE: index = 32; break;
                case WATER_SPLASH: case WATER_WAKE: case WATER_DROP: index = 21; color = 0xFF4060FF; break;
                case SUSPENDED: index = 0; color = 0xFF6699FF; break;
                case SUSPENDED_DEPTH: case TOWN_AURA: index = 0; color = 0xFFAA88AA; break;
                case CRIT: case DAMAGE_INDICATOR: index = 65; color = 0xFFBFA580; break;
                case CRIT_MAGIC: index = 66; color = 0xFF4DCC80; break;
                case SPELL: case SPELL_MOB: case SPELL_MOB_AMBIENT: index = 132; break;
                case SPELL_INSTANT: index = 148; break;
                case SPELL_WITCH: index = 148; color = 0xFFB34DE6; break;
                case DRIP_WATER: index = 112; color = 0xFF4060FF; break;
                case DRIP_LAVA: index = 112; color = 0xFFFF6600; break;
                case VILLAGER_ANGRY: index = 81; break;
                case VILLAGER_HAPPY: index = 82; color = 0xFF66CC33; break;
                case NOTE: index = 64; color = 0xFF66CC33; break;
                case PORTAL: index = 4; color = 0xFFB34DE6; break;
                case DRAGON_BREATH: index = 6; color = 0xFFB34DE6; break;
                case ENCHANTMENT_TABLE: index = 237; break;
                case FLAME: index = 48; break;
                case LAVA: index = 49; break;
                case HEART: index = 80; break;
                case END_ROD: index = 180; break;
                case TOTEM: index = 180; color = 0xFF66CC33; break;
                case SPIT: index = 4; break;
                case SNOWBALL: case ITEM_CRACK: sprite = "minecraft:items/snowball"; break;
                case SLIME: sprite = "minecraft:items/slimeball"; break;
                case BARRIER: sprite = "minecraft:items/barrier"; break;
                case BLOCK_CRACK: case BLOCK_DUST: sprite = "minecraft:blocks/stone"; break;
                case FOOTSTEP: texture = new ResourceLocation("textures/particle/footprint.png"); break;
                case EXPLOSION_LARGE: case EXPLOSION_HUGE:
                    texture = new ResourceLocation("textures/entity/explosion.png");
                    u1 = 0; v1 = 0.5F; u2 = 0.25F; v2 = 0.75F; break;
                case SWEEP_ATTACK:
                    texture = new ResourceLocation("textures/entity/sweep.png");
                    u1 = 0.5F; v1 = 0; u2 = 0.75F; v2 = 0.5F; break;
                default: type = null;
            }
        }
        if (type == null)
        {
            context.batcher.scaledIcon(Icons.PARTICLE, fallbackColor, x, y, size);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int textureId;
        if (sprite != null)
        {
            TextureMap atlas = mc.getTextureMapBlocks();
            TextureAtlasSprite sample = atlas.getAtlasSprite(sprite);
            textureId = atlas.getGlTextureId();
            u1 = sample.getMinU(); v1 = sample.getMinV(); u2 = sample.getMaxU(); v2 = sample.getMaxV();
        }
        else
        {
            int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            mc.getTextureManager().bindTexture(texture);
            textureId = mc.getTextureManager().getTexture(texture).getGlTextureId();
            GlStateManager.bindTexture(oldTexture);
            if (index >= 0)
            {
                u1 = (index % 16) / 16F; v1 = (index / 16) / 16F;
                u2 = u1 + 1 / 16F; v2 = v1 + 1 / 16F;
            }
        }
        context.batcher.texturedBox(textureId, color, x, y, size, size, u1, v1, u2, v2, 1, 1);
    }
    private VanillaParticlePreview() {}
}
