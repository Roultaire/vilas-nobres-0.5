package br.nobrevilas;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Mod de servidor: nao precisa ser instalado no cliente. */
public class NobreVilas implements ModInitializer {
    public static final String MOD_ID = "nobrevilas";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private static VillageManager manager;

    public static VillageManager manager() { return manager; }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            manager = new VillageManager(server);
            manager.load();
            Integrations.logDetected(manager.cfg());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (manager != null) { manager.shutdown(); manager = null; }
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> {
            if (manager != null) manager.onChunkLoad(level, chunk);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (manager != null) manager.tick(); });
        CommandRegistrationCallback.EVENT.register(VilaCommand::register);
        PayloadTypeRegistry.clientboundPlay().register(NpcTalkPayload.TYPE, NpcTalkPayload.CODEC);
        Conquest.register();
        LOG.info("Nobre Vilas carregado.");
    }
}
