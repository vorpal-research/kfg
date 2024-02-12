package org.vorpal.research.kfg.type;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.FrameNode;

class FrameNodeHelper {
    public static int getFrameType(@NotNull FrameNode node) {
        return node.type;
    }
}
