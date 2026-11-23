package com.example.disasters;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

public class NaturalDisastersMod implements ModInitializer {
    public static final String MODID = "naturaldisasters";
    private static int tickCounter = 0, disasterDuration = 0, tsunamiWaveX = 0;
    private static String activeDisaster = "NONE";
    private static Vec3 tornadoPos = null;
    private static BlockPos targetPos = null;
    private static final Random RANDOM = new Random();

    public static final Item METEOR_CALLER = reg("meteor_caller", "METEOR", "Triệu Hồi Mưa Thiên Thạch");
    public static final Item EARTHQUAKE_CALLER = reg("earthquake_caller", "EARTHQUAKE", "Triệu Hồi Động Đất");
    public static final Item ACID_RAIN_CALLER = reg("acid_rain_caller", "ACID_RAIN", "Triệu Hồi Mưa Axit");
    public static final Item TORNADO_CALLER = reg("tornado_caller", "TORNADO", "Triệu Hồi Lốc Xoáy");
    public static final Item LANDSLIDE_CALLER = reg("landslide_caller", "LANDSLIDE", "Triệu Hồi Sạt Lở Đất");
    public static final Item NUKE_CALLER = reg("nuke_caller", "NUKE", "Triệu Hồi Bom Hạt Nhân");
    public static final Item TSUNAMI_CALLER = reg("tsunami_caller", "TSUNAMI", "Triệu Hồi Sóng Thần");
    public static final Item VOLCANO_CALLER = reg("volcano_caller", "VOLCANO", "Triệu Hồi Núi Lửa");
    public static final Item SINKHOLE_CALLER = reg("sinkhole_caller", "SINKHOLE", "Triệu Hồi Hố Sụt");
    public static final Item LIGHTNING_CALLER = reg("lightning_caller", "LIGHTNING_STORM", "Triệu Hồi Bão Sấm Sét");
    public static final Item BLIZZARD_CALLER = reg("blizzard_caller", "BLIZZARD", "Triệu Hồi Bão Tuyết");
    public static final Item ZOMBIE_CALLER = reg("zombie_caller", "ZOMBIE_APOCALYPSE", "Triệu Hồi Đại Dịch Zombie");
    public static final Item DISASTER_STOPPER = reg("disaster_stopper", "STOP", "Dừng Toàn Bộ Thảm Họa");

    private static Item reg(String name, String type, String displayName) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MODID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key, new DisasterItem(new Item.Properties().setId(key).stacksTo(1), type, displayName));
    }

    public static final CreativeModeTab DISASTER_TAB = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(MODID, "disaster_tab"),
        FabricItemGroup.builder().title(Component.literal("Thảm Họa Thiên Nhiên")).icon(() -> new ItemStack(NUKE_CALLER))
            .displayItems((p, out) -> {
                out.accept(METEOR_CALLER); out.accept(EARTHQUAKE_CALLER); out.accept(ACID_RAIN_CALLER);
                out.accept(TORNADO_CALLER); out.accept(LANDSLIDE_CALLER); out.accept(NUKE_CALLER);
                out.accept(TSUNAMI_CALLER); out.accept(VOLCANO_CALLER); out.accept(SINKHOLE_CALLER);
                out.accept(LIGHTNING_CALLER); out.accept(BLIZZARD_CALLER); out.accept(ZOMBIE_CALLER);
                out.accept(DISASTER_STOPPER);
            }).build()
    );

    @Override
    public void onInitialize() {
        Object initTab = DISASTER_TAB;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("disaster").requires(s -> s.hasPermission(2))
                .then(Commands.argument("type", StringArgumentType.word()).executes(ctx -> {
                    startDisaster(ctx.getSource().getLevel(), StringArgumentType.getString(ctx, "type").toUpperCase(), BlockPos.containing(ctx.getSource().getPosition()));
                    return 1;
                })));
        });

        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level.players().isEmpty()) return;
            if (++tickCounter >= 6000) {
                tickCounter = 0;
                String[] list = {"METEOR", "EARTHQUAKE", "ACID_RAIN", "TORNADO", "LANDSLIDE", "NUKE", "TSUNAMI", "VOLCANO", "SINKHOLE", "LIGHTNING_STORM", "BLIZZARD", "ZOMBIE_APOCALYPSE"};
                ServerPlayer rp = level.players().get(RANDOM.nextInt(level.players().size()));
                startDisaster(level, list[RANDOM.nextInt(list.length)], rp.blockPosition());
            }
            if (disasterDuration > 0) {
                disasterDuration--;
                switch (activeDisaster) {
                    case "TORNADO" -> { if (tornadoPos != null) runTornado(level); }
                    case "NUKE" -> { if (targetPos != null) runNuke(level); }
                    case "TSUNAMI" -> { if (targetPos != null) runTsunami(level); }
                    case "VOLCANO" -> { if (targetPos != null) runVolcano(level); }
                    case "SINKHOLE" -> { if (targetPos != null) runSinkhole(level); }
                    default -> { for (ServerPlayer p : level.players()) runPlayerDisaster(level, p); }
                }
                if (disasterDuration == 0) { activeDisaster = "NONE"; tornadoPos = null; targetPos = null; }
            }
        });
    }
package com.example.disasters;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

public class NaturalDisastersMod implements ModInitializer {
    public static final String MODID = "naturaldisasters";
    private static int tickCounter = 0, disasterDuration = 0, tsunamiWaveX = 0;
    private static String activeDisaster = "NONE";
    private static Vec3 tornadoPos = null;
    private static BlockPos targetPos = null;
    private static final Random RANDOM = new Random();

    public static final Item METEOR_CALLER = reg("meteor_caller", "METEOR", "Triệu Hồi Mưa Thiên Thạch");
    public static final Item EARTHQUAKE_CALLER = reg("earthquake_caller", "EARTHQUAKE", "Triệu Hồi Động Đất");
    public static final Item ACID_RAIN_CALLER = reg("acid_rain_caller", "ACID_RAIN", "Triệu Hồi Mưa Axit");
    public static final Item TORNADO_CALLER = reg("tornado_caller", "TORNADO", "Triệu Hồi Lốc Xoáy");
    public static final Item LANDSLIDE_CALLER = reg("landslide_caller", "LANDSLIDE", "Triệu Hồi Sạt Lở Đất");
    public static final Item NUKE_CALLER = reg("nuke_caller", "NUKE", "Triệu Hồi Bom Hạt Nhân");
    public static final Item TSUNAMI_CALLER = reg("tsunami_caller", "TSUNAMI", "Triệu Hồi Sóng Thần");
    public static final Item VOLCANO_CALLER = reg("volcano_caller", "VOLCANO", "Triệu Hồi Núi Lửa");
    public static final Item SINKHOLE_CALLER = reg("sinkhole_caller", "SINKHOLE", "Triệu Hồi Hố Sụt");
    public static final Item LIGHTNING_CALLER = reg("lightning_caller", "LIGHTNING_STORM", "Triệu Hồi Bão Sấm Sét");
    public static final Item BLIZZARD_CALLER = reg("blizzard_caller", "BLIZZARD", "Triệu Hồi Bão Tuyết");
    public static final Item ZOMBIE_CALLER = reg("zombie_caller", "ZOMBIE_APOCALYPSE", "Triệu Hồi Đại Dịch Zombie");
    public static final Item DISASTER_STOPPER = reg("disaster_stopper", "STOP", "Dừng Toàn Bộ Thảm Họa");

    private static Item reg(String name, String type, String displayName) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MODID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key, new DisasterItem(new Item.Properties().setId(key).stacksTo(1), type, displayName));
    }

    public static final CreativeModeTab DISASTER_TAB = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(MODID, "disaster_tab"),
        FabricItemGroup.builder().title(Component.literal("Thảm Họa Thiên Nhiên")).icon(() -> new ItemStack(NUKE_CALLER))
            .displayItems((p, out) -> {
                out.accept(METEOR_CALLER); out.accept(EARTHQUAKE_CALLER); out.accept(ACID_RAIN_CALLER);
                out.accept(TORNADO_CALLER); out.accept(LANDSLIDE_CALLER); out.accept(NUKE_CALLER);
                out.accept(TSUNAMI_CALLER); out.accept(VOLCANO_CALLER); out.accept(SINKHOLE_CALLER);
                out.accept(LIGHTNING_CALLER); out.accept(BLIZZARD_CALLER); out.accept(ZOMBIE_CALLER);
                out.accept(DISASTER_STOPPER);
            }).build()
    );

    @Override
    public void onInitialize() {
        Object initTab = DISASTER_TAB;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("disaster").requires(s -> s.hasPermission(2))
                .then(Commands.argument("type", StringArgumentType.word()).executes(ctx -> {
                    startDisaster(ctx.getSource().getLevel(), StringArgumentType.getString(ctx, "type").toUpperCase(), BlockPos.containing(ctx.getSource().getPosition()));
                    return 1;
                })));
        });

        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level.players().isEmpty()) return;
            if (++tickCounter >= 6000) {
                tickCounter = 0;
                String[] list = {"METEOR", "EARTHQUAKE", "ACID_RAIN", "TORNADO", "LANDSLIDE", "NUKE", "TSUNAMI", "VOLCANO", "SINKHOLE", "LIGHTNING_STORM", "BLIZZARD", "ZOMBIE_APOCALYPSE"};
                ServerPlayer rp = level.players().get(RANDOM.nextInt(level.players().size()));
                startDisaster(level, list[RANDOM.nextInt(list.length)], rp.blockPosition());
            }
            if (disasterDuration > 0) {
                disasterDuration--;
                switch (activeDisaster) {
                    case "TORNADO" -> { if (tornadoPos != null) runTornado(level); }
                    case "NUKE" -> { if (targetPos != null) runNuke(level); }
                    case "TSUNAMI" -> { if (targetPos != null) runTsunami(level); }
                    case "VOLCANO" -> { if (targetPos != null) runVolcano(level); }
                    case "SINKHOLE" -> { if (targetPos != null) runSinkhole(level); }
                    default -> { for (ServerPlayer p : level.players()) runPlayerDisaster(level, p); }
                }
                if (disasterDuration == 0) { activeDisaster = "NONE"; tornadoPos = null; targetPos = null; }
            }
        });
    }
package com.example.disasters;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

public class NaturalDisastersMod implements ModInitializer {
    public static final String MODID = "naturaldisasters";
    private static int tickCounter = 0, disasterDuration = 0, tsunamiWaveX = 0;
    private static String activeDisaster = "NONE";
    private static Vec3 tornadoPos = null;
    private static BlockPos targetPos = null;
    private static final Random RANDOM = new Random();

    public static final Item METEOR_CALLER = reg("meteor_caller", "METEOR", "Triệu Hồi Mưa Thiên Thạch");
    public static final Item EARTHQUAKE_CALLER = reg("earthquake_caller", "EARTHQUAKE", "Triệu Hồi Động Đất");
    public static final Item ACID_RAIN_CALLER = reg("acid_rain_caller", "ACID_RAIN", "Triệu Hồi Mưa Axit");
    public static final Item TORNADO_CALLER = reg("tornado_caller", "TORNADO", "Triệu Hồi Lốc Xoáy");
    public static final Item LANDSLIDE_CALLER = reg("landslide_caller", "LANDSLIDE", "Triệu Hồi Sạt Lở Đất");
    public static final Item NUKE_CALLER = reg("nuke_caller", "NUKE", "Triệu Hồi Bom Hạt Nhân");
    public static final Item TSUNAMI_CALLER = reg("tsunami_caller", "TSUNAMI", "Triệu Hồi Sóng Thần");
    public static final Item VOLCANO_CALLER = reg("volcano_caller", "VOLCANO", "Triệu Hồi Núi Lửa");
    public static final Item SINKHOLE_CALLER = reg("sinkhole_caller", "SINKHOLE", "Triệu Hồi Hố Sụt");
    public static final Item LIGHTNING_CALLER = reg("lightning_caller", "LIGHTNING_STORM", "Triệu Hồi Bão Sấm Sét");
    public static final Item BLIZZARD_CALLER = reg("blizzard_caller", "BLIZZARD", "Triệu Hồi Bão Tuyết");
    public static final Item ZOMBIE_CALLER = reg("zombie_caller", "ZOMBIE_APOCALYPSE", "Triệu Hồi Đại Dịch Zombie");
    public static final Item DISASTER_STOPPER = reg("disaster_stopper", "STOP", "Dừng Toàn Bộ Thảm Họa");

    private static Item reg(String name, String type, String displayName) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MODID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key, new DisasterItem(new Item.Properties().setId(key).stacksTo(1), type, displayName));
    }

    public static final CreativeModeTab DISASTER_TAB = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath(MODID, "disaster_tab"),
        FabricItemGroup.builder().title(Component.literal("Thảm Họa Thiên Nhiên")).icon(() -> new ItemStack(NUKE_CALLER))
            .displayItems((p, out) -> {
                out.accept(METEOR_CALLER); out.accept(EARTHQUAKE_CALLER); out.accept(ACID_RAIN_CALLER);
                out.accept(TORNADO_CALLER); out.accept(LANDSLIDE_CALLER); out.accept(NUKE_CALLER);
                out.accept(TSUNAMI_CALLER); out.accept(VOLCANO_CALLER); out.accept(SINKHOLE_CALLER);
                out.accept(LIGHTNING_CALLER); out.accept(BLIZZARD_CALLER); out.accept(ZOMBIE_CALLER);
                out.accept(DISASTER_STOPPER);
            }).build()
    );

    @Override
    public void onInitialize() {
        Object initTab = DISASTER_TAB;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("disaster").requires(s -> s.hasPermission(2))
                .then(Commands.argument("type", StringArgumentType.word()).executes(ctx -> {
                    startDisaster(ctx.getSource().getLevel(), StringArgumentType.getString(ctx, "type").toUpperCase(), BlockPos.containing(ctx.getSource().getPosition()));
                    return 1;
                })));
        });

        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level.players().isEmpty()) return;
            if (++tickCounter >= 6000) {
                tickCounter = 0;
                String[] list = {"METEOR", "EARTHQUAKE", "ACID_RAIN", "TORNADO", "LANDSLIDE", "NUKE", "TSUNAMI", "VOLCANO", "SINKHOLE", "LIGHTNING_STORM", "BLIZZARD", "ZOMBIE_APOCALYPSE"};
                ServerPlayer rp = level.players().get(RANDOM.nextInt(level.players().size()));
                startDisaster(level, list[RANDOM.nextInt(list.length)], rp.blockPosition());
            }
            if (disasterDuration > 0) {
                disasterDuration--;
                switch (activeDisaster) {
                    case "TORNADO" -> { if (tornadoPos != null) runTornado(level); }
                    case "NUKE" -> { if (targetPos != null) runNuke(level); }
                    case "TSUNAMI" -> { if (targetPos != null) runTsunami(level); }
                    case "VOLCANO" -> { if (targetPos != null) runVolcano(level); }
                    case "SINKHOLE" -> { if (targetPos != null) runSinkhole(level); }
                    default -> { for (ServerPlayer p : level.players()) runPlayerDisaster(level, p); }
                }
                if (disasterDuration == 0) { activeDisaster = "NONE"; tornadoPos = null; targetPos = null; }
            }
        });
    }
