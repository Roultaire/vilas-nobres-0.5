package br.nobrevilas;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Tecla N: olhando para uma entidade abre a conversa (/vila ui falar <uuid>); no ar abre o guia (/vila ui guia).
 * O servidor decide o que mostrar (dialogos nativos), entao nenhuma tela customizada e necessaria.
 */
public class NobreVilasClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(NobreVilas.MOD_ID, "main"));
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "Nobre Vilas: guia / falar com NPC", InputConstants.Type.KEYBOARD, InputConstants.KEY_N, CATEGORY));

        ClientPlayNetworking.registerGlobalReceiver(NpcTalkPayload.TYPE,
            (payload, context) -> context.client().setScreenAndShow(new NpcScreen(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.player == null) continue;
                String cmd = "vila ui guia";
                if (client.hitResult instanceof EntityHitResult hit) {
                    Entity e = hit.getEntity();
                    cmd = "vila ui falar " + e.getUUID();
                }
                client.player.connection.sendCommand(cmd);
            }
        });
    }
}
