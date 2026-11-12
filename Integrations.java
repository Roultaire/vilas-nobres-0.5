package br.nobrevilas;

import net.fabricmc.loader.api.FabricLoader;

/** Deteccao de mods opcionais. Cada integracao so e ativada se o mod estiver instalado; sem ele usamos o vanilla. */
final class Integrations {
    private Integrations() {}

    static boolean loaded(String modId) { return FabricLoader.getInstance().isModLoaded(modId); }

    static void logDetected(Config cfg) {
        NobreVilas.LOG.info("Integracoes: guardas={} (mod {}), moeda={}",
            loaded(cfg.guardModId) ? cfg.guardEntity : cfg.fallbackGuardEntity, cfg.guardModId, cfg.currencyItem);
    }
}
