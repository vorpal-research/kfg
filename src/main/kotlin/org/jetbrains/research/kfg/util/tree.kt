package org.jetbrains.research.kfg.util

interface TreeNode {
    fun getParent(): TreeNode?
    fun getChilds(): Set<TreeNode>
}