package net.minestom.server.network.netty.packet;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a packet which is already framed. (packet id+payload) + optional compression
 * Can be used if you want to send the exact same buffer to multiple clients without processing it more than once.
 */
public class FramedPacket {

    private final ByteBuf body;
    private boolean releaseAll = false;

    public FramedPacket(@NotNull ByteBuf body) {
        this.body = body;
    }

    public FramedPacket(@NotNull ByteBuf body, boolean releaseAll) {
        this.body = body;
        this.releaseAll = releaseAll;
    }

    @NotNull
    public ByteBuf getBody() {
        return body;
    }

    public boolean shouldReleaseAll() {
        return releaseAll;
    }

}
