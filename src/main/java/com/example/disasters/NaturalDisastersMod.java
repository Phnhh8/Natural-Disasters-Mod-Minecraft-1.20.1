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
    private static int tickCounter = 0, disasterDuration = 0;
    private static String activeDisaster = "NONE";
    private static Vec3 tornadoPos = null;
    private static BlockPos nukeTargetPos = null;
    private static final Random RANDOM = new Random();

    public static final Item METEOR_CALLER = registerItem("meteor_caller", "METEOR");
    public static final Item EARTHQUAKE_CALLER = registerItem("earthquake_caller", "EARTHQUAKE");
    public static final Item ACID_RAIN_CALLER = registerItem("acid_rain_caller", "ACID_RAIN");
    public static final Item TORNADO_CALLER = registerItem("tornado_caller", "TORNADO");
    public static final Item LANDSLIDE_CALLER = registerItem("landslide_caller", "LANDSLIDE");
    public static final Item NUKE_CALLER = registerItem("nuke_caller", "NUKE");
    public static final Item DISASTER_STOPPER = registerItem("disaster_stopper", "STOP");

    private static Item registerItem(String name, String type) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MODID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key, new DisasterItem(new Item.Properties().setId(key).stacksTo(1), type));
    }

    public static final CreativeModeTab DISASTER_TAB = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB,
        ResourceLocation.fromNamespaceAndPath(MODID, "disaster_tab"),
        FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.naturaldisasters"))
            .icon(() -> new ItemStack(NUKE_CALLER))
            .displayItems((params, out) -> {
                out.accept(METEOR_CALLER); out.accept(EARTHQUAKE_CALLER); out.accept(ACID_RAIN_CALLER);
                out.accept(TORNADO_CALLER); out.accept(LANDSLIDE_CALLER); out.accept(NUKE_CALLER);
                out.accept(DISASTER_STOPPER);
            }).build()
    );
    @Override
    public void onInitialize() {
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
                String[] list = {"METEOR", "EARTHQUAKE", "ACID_RAIN", "TORNADO", "LANDSLIDE", "NUKE"};
                ServerPlayer rp = level.players().get(RANDOM.nextInt(level.players().size()));
                startDisaster(level, list[RANDOM.nextInt(list.length)], rp.blockPosition());
            }
            if (disasterDuration > 0) {
                disasterDuration--;
                if (activeDisaster.equals("TORNADO") && tornadoPos != null) runTornado(level);
                else if (activeDisaster.equals("NUKE") && nukeTargetPos != null) runNuke(level);
                else for (ServerPlayer p : level.players()) runPlayerDisaster(level, p);
                if (disasterDuration == 0) { activeDisaster = "NONE"; tornadoPos = null; nukeTargetPos = null; }
            }
        });
    }

    public static class DisasterItem extends Item {
        private final String type;
        public DisasterItem(Properties props, String type) { super(props); this.type = type; }
        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide() && level instanceof ServerLevel sl) {
                if (type.equals("STOP")) {
                    activeDisaster = "NONE"; disasterDuration = 0; tornadoPos = null; nukeTargetPos = null;
                    player.sendSystemMessage(Component.literal("§a[HỆ THỐNG] §fĐã dừng toàn bộ thảm họa!"));
                } else startDisaster(sl, type, player.blockPosition());
                player.getCooldowns().addCooldown(this.getDefaultInstance(), 40);
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static void startDisaster(ServerLevel level, String type, BlockPos pos) {
        activeDisaster = type;
        disasterDuration = type.equals("NUKE") ? 200 : 400;
        if (type.equals("TORNADO")) tornadoPos = new Vec3(pos.getX() + 15, pos.getY(), pos.getZ() + 15);
        else if (type.equals("NUKE")) nukeTargetPos = pos.offset(RANDOM.nextInt(16) - 8, 0, RANDOM.nextInt(16) - 8);
        for (ServerPlayer p : level.players()) p.sendSystemMessage(Component.literal("§c[CẢNH BÁO] §eThiên tai: §l" + type));
    }

    private static void runTornado(ServerLevel level) {
        Vec3 dir = level.players().get(0).position().subtract(tornadoPos).normalize();
        tornadoPos = tornadoPos.add(dir.x * 0.25, 0, dir.z * 0.25);
        for (int y = 0; y < 30; y++) {
            double r = 1.5 + y * 0.25;
            for (int i = 0; i < 6; i++) {
                double a = disasterDuration * 0.4 + i * Math.PI / 3 + y * 0.2;
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, tornadoPos.x + Math.cos(a) * r, tornadoPos.y + y, tornadoPos.z + Math.sin(a) * r, 1, 0, 0, 0, 0.01);
            }
        }
        for (Entity e : level.getEntities(null, new AABB(tornadoPos.x - 18, tornadoPos.y - 5, tornadoPos.z - 18, tornadoPos.x + 18, tornadoPos.y + 35, tornadoPos.z + 18))) {
            double dx = tornadoPos.x - e.getX(), dz = tornadoPos.z - e.getZ();
            e.setDeltaMovement(new Vec3(dx * 0.08 - dz * 0.12, 0.35, dz * 0.08 + dx * 0.12)); e.hurtMarked = true;
        }
    }

    private static void runNuke(ServerLevel level) {
        if (disasterDuration > 100) {
            if (disasterDuration % 20 == 0) {
                for (ServerPlayer p : level.players()) {
                    p.sendSystemMessage(Component.literal("§4§l[HẠT NHÂN] §eNổ sau: §c" + ((disasterDuration - 100) / 20) + "s"));
                    level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 1f, 0.5f);
                }
            }
            level.sendParticles(ParticleTypes.FLAME, nukeTargetPos.getX(), nukeTargetPos.getY() + 2, nukeTargetPos.getZ(), 15, 0.5, 10, 0.5, 0.05);
        } else if (disasterDuration == 100) {
            level.explode(null, nukeTargetPos.getX(), nukeTargetPos.getY(), nukeTargetPos.getZ(), 22f, true, Level.ExplosionInteraction.BLOCK);
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(nukeTargetPos).inflate(45))) {
                le.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 1));
                le.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 300, 0));
                le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            }
        } else level.sendParticles(ParticleTypes.ASH, nukeTargetPos.getX(), nukeTargetPos.getY() + 15, nukeTargetPos.getZ(), 80, 20, 10, 20, 0.02);
    }

    private static void runPlayerDisaster(ServerLevel level, ServerPlayer p) {
        BlockPos pos = p.blockPosition();
        switch (activeDisaster) {
            case "METEOR" -> {
                if (disasterDuration % 20 == 0) {
                    LargeFireball fb = new LargeFireball(level, p, new Vec3(0, -1.5, 0), 3);
                    fb.setPos(pos.getX() + RANDOM.nextInt(30) - 15, pos.getY() + 35, pos.getZ() + RANDOM.nextInt(30) - 15);
                    level.addFreshEntity(fb);
                }
            }
            case "EARTHQUAKE" -> {
                p.setDeltaMovement(p.getDeltaMovement().add((RANDOM.nextDouble() - 0.5) * 0.4, 0.15, (RANDOM.nextDouble() - 0.5) * 0.4)); p.hurtMarked = true;
                if (disasterDuration % 5 == 0) {
                    BlockPos cp = pos.offset(RANDOM.nextInt(12) - 6, -1, RANDOM.nextInt(12) - 6);
                    if (!level.getBlockState(cp).isAir() && level.getBlockState(cp).getDestroySpeed(level, cp) >= 0) level.destroyBlock(cp, false);
                }
            }
            case "ACID_RAIN" -> {
                level.setWeatherParameters(0, 400, true, true);
                if (level.canSeeSky(pos)) {
                    p.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 1));
                    if (level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) level.setBlockAndUpdate(pos.below(), Blocks.DIRT.defaultBlockState());
                }
            }
            case "LANDSLIDE" -> {
                if (disasterDuration % 3 == 0) {
                    for (int i = 0; i < 4; i++) {
                        BlockPos sp = pos.offset(RANDOM.nextInt(20) - 10, RANDOM.nextInt(8) + 2, RANDOM.nextInt(20) - 10);
                        BlockState st = level.getBlockState(sp);
                        if (!st.isAir() && (st.is(Blocks.DIRT) || st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.STONE) || st.is(Blocks.SAND))) {
                            level.removeBlock(sp, false); FallingBlockEntity.fall(level, sp, st);
                        } else if (st.isAir()) FallingBlockEntity.fall(level, sp.above(5), RANDOM.nextBoolean() ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState());
                    }
                }
            }
        }
    }
}

