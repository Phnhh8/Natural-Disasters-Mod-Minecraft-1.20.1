package com.example.disasters;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod("naturaldisasters")
public class NaturalDisastersMod {
    private static int tickCounter = 0;
    private static String activeDisaster = "NONE";
    private static int disasterDuration = 0;
    private static final Random RANDOM = new Random();

    public NaturalDisastersMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("disaster")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("type", StringArgumentType.word())
                    .executes(context -> {
                        String type = StringArgumentType.getString(context, "type").toUpperCase();
                        startDisaster(context.getSource().getLevel(), type);
                        return 1;
                    })
                )
        );
    }

    public static void startDisaster(ServerLevel level, String type) {
        activeDisaster = type;
        disasterDuration = 400; // Kéo dài 20 giây (400 ticks)
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal("§c[CẢNH BÁO THẢM HỌA] §eThiên tai đang ập đến: §l" + type));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) return;
        ServerLevel level = (ServerLevel) event.level;
        if (level.players().isEmpty()) return;

        // Tự động gọi thiên tai mỗi 5 phút (6000 ticks)
        tickCounter++;
        if (tickCounter >= 6000) {
            tickCounter = 0;
            String[] disasters = {"METEOR", "EARTHQUAKE", "ACID_RAIN"};
            startDisaster(level, disasters[RANDOM.nextInt(disasters.length)]);
        }

        if (disasterDuration > 0) {
            disasterDuration--;
            for (ServerPlayer player : level.players()) {
                executeDisasterEffect(level, player);
            }
            if (disasterDuration == 0) {
                activeDisaster = "NONE";
            }
        }
    }

    private void executeDisasterEffect(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition();

        switch (activeDisaster) {
            case "METEOR":
                if (disasterDuration % 20 == 0) {
                    int offsetX = RANDOM.nextInt(30) - 15;
                    int offsetZ = RANDOM.nextInt(30) - 15;
                    LargeFireball fireball = new LargeFireball(level, player, 0, -1.5, 0, 3);
                    fireball.setPos(pos.getX() + offsetX, pos.getY() + 35, pos.getZ() + offsetZ);
                    level.addFreshEntity(fireball);
                }
                break;

            case "EARTHQUAKE":
                // Hất tung nhẹ người chơi và phá hủy ngẫu nhiên block xung quanh
                double shakeX = (RANDOM.nextDouble() - 0.5) * 0.4;
                double shakeZ = (RANDOM.nextDouble() - 0.5) * 0.4;
                player.setDeltaMovement(player.getDeltaMovement().add(shakeX, 0.15, shakeZ));
                player.hurtMarked = true;

                if (disasterDuration % 5 == 0) {
                    BlockPos crackPos = pos.offset(RANDOM.nextInt(12) - 6, -1, RANDOM.nextInt(12) - 6);
                    if (!level.getBlockState(crackPos).isAir() && level.getBlockState(crackPos).getDestroySpeed(level, crackPos) >= 0) {
                        level.destroyBlock(crackPos, false);
                    }
                }
                break;

            case "ACID_RAIN":
                level.setWeatherParameters(0, 400, true, true);
                // Nếu người chơi đứng ngoài trời không có mái che
                if (level.canSeeSky(pos)) {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 1));
                    // Ăn mòn cỏ thành đất
                    BlockPos ground = pos.below();
                    if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
                        level.setBlockAndUpdate(ground, Blocks.DIRT.defaultBlockState());
                    }
                }
                break;
        }
    }
}
