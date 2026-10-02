package net.ryzlar.strategem;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.ryzlar.LaserMod;

import java.util.Collection;
import java.util.List;

/**
 * Operator commands, mainly for testing:
 * <ul>
 *     <li>{@code /strategem cooldown reset [players]} clears stratagem cooldowns</li>
 *     <li>{@code /strategem call <name>} calls a stratagem in on the block you are looking at (no ball, no cooldown)</li>
 * </ul>
 */
public final class StrategemCommands {

    private StrategemCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("strategem")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("cooldown").then(Commands.literal("reset")
                                .executes(ctx -> reset(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayers(ctx, "players"))))))
                        .then(Commands.literal("call").then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Strategems.all().stream().map(s -> s.id().getPath()), builder))
                                .executes(ctx -> call(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
        ));
    }

    private static int reset(CommandSourceStack source, Collection<ServerPlayer> players) {
        players.forEach(StrategemCooldowns::reset);
        source.sendSuccess(() -> Component.literal("Reset stratagem cooldowns for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int call(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Strategem strategem = Strategems.get(Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, name));
        if (strategem == null) {
            source.sendFailure(Component.literal("Unknown stratagem: " + name));
            return 0;
        }
        HitResult hit = player.pick(160.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            source.sendFailure(Component.literal("Look at a block (within 160 blocks)"));
            return 0;
        }
        strategem.attack().execute(new Attack.Context(player.level(), strategem, blockHit.getLocation(),
                blockHit.getBlockPos().relative(blockHit.getDirection()), blockHit.getDirection(), player));
        source.sendSuccess(() -> Component.literal("Called in ").append(strategem.displayName()), true);
        return 1;
    }
}
