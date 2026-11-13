package br.nobrevilas;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Servidor -> cliente: conteudo de uma fala de NPC.
 * head    = nome \u0001 profissao \u0001 cor-do-nome \u0001 titulo \u0001 cor-do-titulo
 * body    = texto da fala (com \n)
 * options = rotulo \u0001 comando  (entradas separadas por \u0002)
 */
public record NpcTalkPayload(String head, String body, String options) implements CustomPacketPayload {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(NobreVilas.MOD_ID, "npc_talk");
    public static final CustomPacketPayload.Type<NpcTalkPayload> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcTalkPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, NpcTalkPayload::head,
        ByteBufCodecs.STRING_UTF8, NpcTalkPayload::body,
        ByteBufCodecs.STRING_UTF8, NpcTalkPayload::options,
        NpcTalkPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
