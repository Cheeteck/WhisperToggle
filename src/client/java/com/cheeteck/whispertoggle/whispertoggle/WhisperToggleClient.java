package com.cheeteck.whispertoggle.whispertoggle;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

public class WhisperToggleClient implements ClientModInitializer {

    private static String whisperTarget = null;
    private static boolean whisperMode = false;
    private static KeyBinding toggleKey;

    private static final SuggestionProvider<FabricClientCommandSource> SUGGEST_PLAYERS = (CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) -> {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) {
            return CompletableFuture.completedFuture(builder.build());
        }

        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().getName();
            if (name.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(name);
            }
        }

        return builder.buildFuture();
    };

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.whispertoggle.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.chat"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                whisperMode = !whisperMode;
                String msg;
                if (whisperMode) {
                    if (whisperTarget != null) {
                        msg = "§6[WhisperToggle] §fON → " + whisperTarget;
                    } else {
                        whisperMode = false;
                        msg = "No whisper target set! Use /wt <player> to set one.";
                    }
                } else {
                    msg = "§6[WhisperToggle] §fOFF";
                }
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(msg), true);
                }
            }
        });

        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (whisperMode && whisperTarget != null && !message.startsWith("/")) {
                MinecraftClient.getInstance().getNetworkHandler()
                        .sendChatCommand("w " + whisperTarget + " " + message);
                return false;
            }
            return true;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("wt")
                    .then(ClientCommandManager.argument("target", StringArgumentType.word())
                            .suggests(SUGGEST_PLAYERS)
                            .executes(ctx -> {
                                whisperTarget = StringArgumentType.getString(ctx, "target");
                                MinecraftClient.getInstance().player.sendMessage(Text.literal("§6[WhisperToggle] §fWhisper target set to: " + whisperTarget), true);
                                return 1;
                            })
                    )
            );

            HudRenderCallback.EVENT.register((DrawContext drawContext, float tickDelta) -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null &&
                        client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen &&
                        whisperMode && whisperTarget != null) {

                    String status = "§7[Whispering to: §f" + whisperTarget + "§7]";
                    int x = 4;
                    int y = client.getWindow().getScaledHeight() - 25;

                    drawContext.drawText(
                            client.textRenderer,
                            status,
                            x,
                            y,
                            0xFFFFFF,
                            true
                    );
                }
            });

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.world == null || client.player == null) return;
                // No offline check here anymore
            });

            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                whisperMode = false;
                whisperTarget = null;
            });
        });
    }
}
