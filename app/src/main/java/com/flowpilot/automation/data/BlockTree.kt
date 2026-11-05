package com.flowpilot.automation.data

/** Helpers for nested step trees used by the visual editor. */
object BlockTree {
    fun find(steps: List<Block>, id: String): Block? {
        for (b in steps) {
            if (b.id == id) return b
            find(b.children, id)?.let { return it }
            find(b.elseChildren, id)?.let { return it }
            for (br in b.elseIfBranches) {
                find(br.children, id)?.let { return it }
            }
        }
        return null
    }

    fun update(steps: List<Block>, id: String, transform: (Block) -> Block): List<Block> =
        steps.map { b ->
            when {
                b.id == id -> transform(b)
                else -> b.copy(
                    children = update(b.children, id, transform),
                    elseChildren = update(b.elseChildren, id, transform),
                    elseIfBranches = b.elseIfBranches.map { br ->
                        br.copy(children = update(br.children, id, transform))
                    }
                )
            }
        }

    fun remove(steps: List<Block>, id: String): List<Block> =
        steps.mapNotNull { b ->
            if (b.id == id) null
            else b.copy(
                children = remove(b.children, id),
                elseChildren = remove(b.elseChildren, id),
                elseIfBranches = b.elseIfBranches.map { br ->
                    br.copy(children = remove(br.children, id))
                }
            )
        }

    fun insertChild(steps: List<Block>, parentId: String, branch: String, child: Block): List<Block> =
        update(steps, parentId) { parent ->
            when (branch) {
                "else" -> parent.copy(elseChildren = parent.elseChildren + child)
                else -> parent.copy(children = parent.children + child)
            }
        }

    fun duplicate(steps: List<Block>, id: String): List<Block> {
        fun deepCopy(b: Block): Block = b.copy(
            id = java.util.UUID.randomUUID().toString(),
            children = b.children.map { deepCopy(it) },
            elseChildren = b.elseChildren.map { deepCopy(it) },
            elseIfBranches = b.elseIfBranches.map { br ->
                br.copy(id = java.util.UUID.randomUUID().toString(), children = br.children.map { deepCopy(it) })
            }
        )
        fun dupIn(list: List<Block>): List<Block> {
            val out = mutableListOf<Block>()
            for (b in list) {
                out += b.copy(
                    children = dupIn(b.children),
                    elseChildren = dupIn(b.elseChildren),
                    elseIfBranches = b.elseIfBranches.map { it.copy(children = dupIn(it.children)) }
                )
                if (b.id == id) out += deepCopy(b)
            }
            return out
        }
        return dupIn(steps)
    }

    fun moveUp(steps: List<Block>, id: String): List<Block> {
        val idx = steps.indexOfFirst { it.id == id }
        if (idx > 0) {
            val m = steps.toMutableList()
            val t = m[idx - 1]; m[idx - 1] = m[idx]; m[idx] = t
            return m
        }
        return steps.map { b ->
            b.copy(
                children = moveUp(b.children, id),
                elseChildren = moveUp(b.elseChildren, id)
            )
        }
    }

    fun moveDown(steps: List<Block>, id: String): List<Block> {
        val idx = steps.indexOfFirst { it.id == id }
        if (idx >= 0 && idx < steps.lastIndex) {
            val m = steps.toMutableList()
            val t = m[idx + 1]; m[idx + 1] = m[idx]; m[idx] = t
            return m
        }
        return steps.map { b ->
            b.copy(
                children = moveDown(b.children, id),
                elseChildren = moveDown(b.elseChildren, id)
            )
        }
    }

    fun setEnabledFlag(block: Block, disabled: Boolean): Block =
        block.copy(params = block.params + ("__disabled" to if (disabled) "true" else "false"))

    fun isDisabled(block: Block): Boolean = block.paramBool("__disabled", false)
}
