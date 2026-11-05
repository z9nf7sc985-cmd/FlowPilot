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
    fun insertChild_elseBranch() {
        val parent = Block(type = BlockType.IF, params = mapOf("op" to "eq"))
        val child = Block(type = BlockType.TOAST, params = mapOf("text" to "no"))
        val steps = BlockTree.insertChild(listOf(parent), parent.id, "else", child)
        assertEquals(0, steps[0].children.size)
        assertEquals(1, steps[0].elseChildren.size)
        assertEquals(child.id, steps[0].elseChildren[0].id)
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

    @Test
    fun moveUp_and_moveDown_root() {
        val a = Block(type = BlockType.WAIT, params = mapOf("ms" to "1"))
        val b = Block(type = BlockType.WAIT, params = mapOf("ms" to "2"))
        val c = Block(type = BlockType.WAIT, params = mapOf("ms" to "3"))
        val steps = listOf(a, b, c)
        val up = BlockTree.moveUp(steps, c.id)
        assertEquals(listOf(a.id, c.id, b.id), up.map { it.id })
        val down = BlockTree.moveDown(steps, a.id)
        assertEquals(listOf(b.id, a.id, c.id), down.map { it.id })
        assertEquals(steps.map { it.id }, BlockTree.moveUp(steps, a.id).map { it.id })
    }

    @Test
    fun disable_flag() {
        val b = Block(type = BlockType.LOG, params = mapOf("message" to "x"))
        assertFalse(BlockTree.isDisabled(b))
        val d = BlockTree.setEnabledFlag(b, disabled = true)
        assertTrue(BlockTree.isDisabled(d))
        val e = BlockTree.setEnabledFlag(d, disabled = false)
        assertFalse(BlockTree.isDisabled(e))
    }
}
