package com.example.disasters;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Random;

@Mod("naturaldisasters")
public class NaturalDisastersMod {
    public static final String MODID = "naturaldisasters";

    private static int tickCounter = 0;
    private static String activeDisaster = "NONE";
    private static int disasterDuration = 0;
    private static final Random RANDOM = new Random();

    // Đăng ký vật phẩm (Items) và Tab Sáng tạo (Creative Tab)
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> METEOR_CALLER = ITEMS.register("meteor_caller",
            () -> new DisasterTriggerItem(new Item.Properties().stacksTo(1), "METEOR"));

    public static final RegistryObject<Item> EARTHQUAKE_CALLER = ITEMS.register("earthquake_caller",
            () -> new DisasterTriggerItem(new Item.Properties().stacksTo(1), "EARTHQUAKE"));

    public static final RegistryObject<Item> ACID_RAIN_CALLER = ITEMS.register("acid_rain_caller",
            () -> new DisasterTriggerItem(new Item.Properties().stacksTo(1), "ACID_RAIN"));

    public static final RegistryObject<Item> DISASTER_STOPPER = ITEMS.register("disaster_stopper",
            () -> new DisasterTriggerItem(new Item.Properties().stacksTo(1), "STOP"));

    public static final RegistryObject<CreativeModeTab> DISASTER_TAB = CREATIVE_MODE_TABS.register("disaster_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.naturaldisasters"))
                    .icon(() -> new ItemStack(METEOR_CALLER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(METEOR_CALLER.get());
                        output.accept(EARTHQUAKE_CALLER.get());
                        output.accept(ACID_RAIN_CALLER.get());
                        output.accept(DISASTER_STOPPER.get());
                    }).build());

    public NaturalDisastersMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
    }

    // Lớp xử lý khi người chơi chuột phải vào vật phẩm triệu hồi
    public static class DisasterTriggerItem extends Item {
        private final String disasterType;

        public DisasterTriggerItem(Properties properties, String disasterType) {
            super(properties);
            this.disasterType = disasterType;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                if (disasterType.equals("STOP")) {
                    activeDisaster = "NONE";
                    disasterDuration = 0;
                    player.sendSystemMessage(Component.literal("§a[HỆ THỐNG] §fĐã dừng toàn bộ thảm họa thiên nhiên!"));
                } else {
                    startDisaster(serverLevel, disasterType);
                }
                player.getCooldowns().addCooldown(this, 40);
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
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
        disasterDuration = 400; // Kéo dài 20 giây
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal("§c[CẢNH BÁO THẢM HỌA] §eThiên tai đang ập đến: §l" + type));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) return;
        ServerLevel level = (ServerLevel) event.level;
        if (level.players().isEmpty()) return;

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
                if (level.canSeeSky(pos)) {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 1));
                    BlockPos ground = pos.below();
                    if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) {
                        level.setBlockAndUpdate(ground, Blocks.DIRT.defaultBlockState());
                    }
                }
                break;
        }
    }
}
