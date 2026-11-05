package com.flowpilot.automation.data

import org.junit.Assert.*
import org.junit.Test

class BlockTreeTest {
    @Test
    fun insertChild_andFind() {
        val parent = Block(type = BlockType.IF, params = mapOf("op" to "eq"))
        val child = Block(type = BlockType.LOG, params = mapOf("message" to "hi"))
        val steps = BlockTree.insertChild(listOf(parent), parent.id, "then", child)
        assertEquals(1, steps[0].children.size)
        assertNotNull(BlockTree.find(steps, child.id))
    }

    @Test
    fun remove_nested() {
        val child = Block(type = BlockType.TOAST, params = mapOf("text" to "x"))
        val parent = Block(type = BlockType.FOR, params = mapOf("count" to "2"), children = listOf(child))
        val steps = BlockTree.remove(listOf(parent), child.id)
        assertTrue(steps[0].children.isEmpty())
        assertNotNull(BlockTree.find(steps, parent.id))
    }

    @Test
    fun duplicate_newIds() {
        val a = Block(type = BlockType.WAIT, params = mapOf("ms" to "100"))
        val steps = listOf(a)
        val out = BlockTree.duplicate(steps, a.id)
        assertEquals(2, out.size)
        assertNotEquals(out[0].id, out[1].id)
    }
}
