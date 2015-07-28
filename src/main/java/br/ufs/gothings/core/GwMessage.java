package br.ufs.gothings.core;

import io.netty.buffer.ByteBuf;

/**
 * @author Wagner Macedo
 */
public interface GwMessage {
    ByteBuf payload();

    GwHeaders headers();
}
