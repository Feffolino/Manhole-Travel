// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.travel.PryHandler;
import it.ratlab.manholes.travel.TravelHandler;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ManholeNetworking {
    /** Set by the client entry point; stays a no-op on a dedicated server (which never receives these). */
    public static Consumer<OpenTravelScreenPayload> clientOpenScreen = p -> {};
    public static Consumer<FadePayload> clientFade = p -> {};
    public static Consumer<PryStatePayload> clientPryState = p -> {};
    public static Consumer<TravelAnimPayload> clientTravelAnim = p -> {};
    public static Consumer<NetworkSyncPayload> clientNetworkSync = p -> {};

    private ManholeNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("5");
        r.playToClient(OpenTravelScreenPayload.TYPE, OpenTravelScreenPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientOpenScreen.accept(p)));
        r.playToClient(FadePayload.TYPE, FadePayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientFade.accept(p)));
        r.playToClient(NetworkSyncPayload.TYPE, NetworkSyncPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientNetworkSync.accept(p)));
        r.playToClient(TravelAnimPayload.TYPE, TravelAnimPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientTravelAnim.accept(p)));
        r.playToClient(PryStatePayload.TYPE, PryStatePayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientPryState.accept(p)));
        r.playToServer(MashPressPayload.TYPE, MashPressPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        PryHandler.handleMash(sp);
                    }
                }));
        r.playToServer(RenameNodePayload.TYPE, RenameNodePayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        TravelHandler.handleRename(sp, p.node(), p.name());
                    }
                }));
        r.playToServer(ShareHomePayload.TYPE, ShareHomePayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        it.ratlab.manholes.data.NodeRecord n = it.ratlab.manholes.data.ManholeData.get(sp.server).node(p.node());
                        if (n != null && it.ratlab.manholes.travel.Access.canSee(sp, n)) {
                            it.ratlab.manholes.travel.HomeManholes.setShared(sp.server, sp, n, p.shared());
                        }
                    }
                }));
        r.playToServer(TravelRequestPayload.TYPE, TravelRequestPayload.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        TravelHandler.handleRequest(sp, p.from(), p.to());
                    }
                }));
    }
}
