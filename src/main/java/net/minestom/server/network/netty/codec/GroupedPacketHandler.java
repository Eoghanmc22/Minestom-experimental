package net.minestom.server.network.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minestom.server.network.netty.packet.FramedPacket;

public class GroupedPacketHandler extends MessageToByteEncoder<FramedPacket> {

    @Override
    protected void encode(ChannelHandlerContext ctx, FramedPacket msg, ByteBuf out) {
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, FramedPacket msg, boolean preferDirect) {
        final ByteBuf byteBuf = msg.getBody().retainedSlice();
        if (msg.shouldReleaseAll()) {
            final int i = msg.getBody().refCnt() - 1;
            if (i > 0) {
                msg.getBody().release(i);
            }
        }
        return byteBuf;
    }

}
