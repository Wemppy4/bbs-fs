package mchorse.bbs_mod.actions;

import com.google.common.collect.MapMaker;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stat;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

public class SuperFakePlayer extends ServerPlayerEntity
{
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("12345678-9ABC-DEF1-2345-6789ABCDEF69"), "[BBS Player]");
    private static final Map<SuperFakePlayer.FakePlayerKey, SuperFakePlayer> FAKE_PLAYER_MAP = new MapMaker().weakValues().makeMap();

    public static SuperFakePlayer get(ServerWorld world)
    {
        Objects.requireNonNull(world, "World may not be null.");

        return FAKE_PLAYER_MAP.computeIfAbsent(new SuperFakePlayer.FakePlayerKey(world, PROFILE), key -> new SuperFakePlayer(key.world, key.profile));
    }

    protected SuperFakePlayer(ServerWorld world, GameProfile profile)
    {
        super(world.getServer(), world, profile, SyncedClientOptions.createDefault());

        this.networkHandler = new SuperFakePlayerNetworkHandler(this);
    }

    @Override
    public PermissionPredicate getPermissions()
    {
        return PermissionPredicate.ALL;
    }

    @Override
    public CommandOutput getCommandOutput()
    {
        return new CommandOutput()
        {
            @Override
            public void sendMessage(Text message)
            {}

            @Override
            public boolean shouldReceiveFeedback()
            {
                return false;
            }

            @Override
            public boolean shouldTrackOutput()
            {
                return false;
            }

            @Override
            public boolean shouldBroadcastConsoleToOps()
            {
                return false;
            }
        };
    }

    @Override
    public void tick()
    {}

    @Override
    public void setClientOptions(SyncedClientOptions settings)
    {}

    @Override
    public void increaseStat(Stat<?> stat, int amount)
    {}

    @Override
    public void resetStat(Stat<?> stat)
    {}

    @Override
    public boolean isInvulnerableTo(ServerWorld world, DamageSource damageSource)
    {
        return true;
    }

    @Nullable
    @Override
    public Team getScoreboardTeam()
    {
        return null;
    }

    @Override
    public void sleep(BlockPos pos)
    {}

    @Override
    public boolean startRiding(Entity entity, boolean force, boolean shouldCancelInteract)
    {
        return false;
    }

    @Override
    public void openEditSignScreen(SignBlockEntity sign, boolean front)
    {}

    @Override
    public OptionalInt openHandledScreen(@Nullable NamedScreenHandlerFactory factory)
    {
        return OptionalInt.empty();
    }

    @Override
    public void openHorseInventory(AbstractHorseEntity horse, Inventory inventory)
    {}

    private record FakePlayerKey(ServerWorld world, GameProfile profile)
    {}
}