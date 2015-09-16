package br.ufs.gothings.gateway.block;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Wagner Macedo
 */
public enum BlockId {
    COMMUNICATION_MANAGER,
    INPUT_CONTROLLER,
    INTERCONNECTION_CONTROLLER,
    OUTPUT_CONTROLLER;

    // Define to where each block is allowed to be forwarded
    static {
        COMMUNICATION_MANAGER
                .to(INPUT_CONTROLLER, INTERCONNECTION_CONTROLLER);
        INPUT_CONTROLLER
                .to(INTERCONNECTION_CONTROLLER);
        INTERCONNECTION_CONTROLLER
                .to(COMMUNICATION_MANAGER, OUTPUT_CONTROLLER);
        OUTPUT_CONTROLLER
                .to(COMMUNICATION_MANAGER);
    }

    /* Enum logic */

    private final Set<BlockId> forwardsTo = new HashSet<>(2);

    private void to(final BlockId... blockIds) {
        Collections.addAll(forwardsTo, blockIds);
    }

    public boolean canForward(BlockId blockId) {
        return forwardsTo.contains(blockId);
    }
}
