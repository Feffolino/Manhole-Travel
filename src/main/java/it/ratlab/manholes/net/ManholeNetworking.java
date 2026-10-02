// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.travel.Access;
import it.ratlab.manholes.travel.HomeManholes;
import it.ratlab.manholes.travel.PryHandler;
import it.ratlab.manholes.travel.TravelHandler;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ManholeNetworking {
    public static final String PROTOCOL_VERSION = "5";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Manholes.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /** Set by the client entry point; stays a no-op on a dedicated server (which never receives these). */
    public static Consumer<OpenTravelScreenPayload> clientOpenScreen = p -> {};
    public static Consumer<FadePayload> clientFade = p -> {};
    public static Consumer<PryStatePayload> clientPryState = p -> {};
    public static Consumer<TravelAnimPayload> clientTravelAnim = p -> {};
    public static Consumer<NetworkSyncPayload> clientNetworkSync = p -> {};

    private ManholeNetworking() {}

    public static void init() {
        int id = 0;
        // Server -> Client
        CHANNEL.registerMessage(id++, OpenTravelScreenPayload.class,
                OpenTravelScreenPayload::encode, OpenTravelScreenPayload::decode,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> clientOpenScreen.accept(msg));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, FadePayload.class,
                FadePayload::encode, FadePayload::decode,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> clientFade.accept(msg));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, NetworkSyncPayload.class,
                NetworkSyncPayload::encode, NetworkSyncPayload::decode,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> clientNetworkSync.accept(msg));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, TravelAnimPayload.class,
                TravelAnimPayload::encode, TravelAnimPayload::decode,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> clientTravelAnim.accept(msg));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, PryStatePayload.class,
                PryStatePayload::encode, PryStatePayload::decode,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> clientPryState.accept(msg));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        // Client -> Server
        CHANNEL.registerMessage(id++, MashPressPayload.class,
                MashPressPayload::encode, MashPressPayload::decode,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> {
                        ServerPlayer sp = ctx.getSender();
                        if (sp != null) {
                            PryHandler.handleMash(sp);
                        }
                    });
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, RenameNodePayload.class,
                RenameNodePayload::encode, RenameNodePayload::decode,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> {
                        ServerPlayer sp = ctx.getSender();
                        if (sp != null) {
                            TravelHandler.handleRename(sp, msg.node(), msg.name());
                        }
                    });
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, ShareHomePayload.class,
                ShareHomePayload::encode, ShareHomePayload::decode,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> {
                        ServerPlayer sp = ctx.getSender();
                        if (sp != null) {
                            NodeRecord n = ManholeData.get(sp.server).node(msg.node());
                            if (n != null && Access.canSee(sp, n)) {
                                HomeManholes.setShared(sp.server, sp, n, msg.shared());
                            }
                        }
                    });
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, TravelRequestPayload.class,
                TravelRequestPayload::encode, TravelRequestPayload::decode,
                (msg, ctxSupplier) -> {
                    NetworkEvent.Context ctx = ctxSupplier.get();
                    ctx.enqueueWork(() -> {
                        ServerPlayer sp = ctx.getSender();
                        if (sp != null) {
                            TravelHandler.handleRequest(sp, msg.from(), msg.to());
                        }
                    });
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
