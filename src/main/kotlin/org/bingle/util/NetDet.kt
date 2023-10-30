package com.creatotronik.util

import kotlin.math.ceil
import kotlin.math.log
import kotlin.math.pow

/**
 * Implement a deterministic network (graph)
 *
 * Each node in the graph has a number of edges between 1 and `treeOrder`
 * One edge points "up" and N "down"
 *
 * All nodes are connected in a deterministic fashion, so that any
 * graph generated from a (`numberNodes`, `treeOrder`) tuple will behave
 * identically.
 *
 * The graph is implemented as two trees with an intermediate row joining the
 * bottoms of the trees, with the root nodes being interconnected
 *
 * `fill` initialises the graph
 * `flood` implements a flood fill, such that a message can be dispatched from any
 *   node and will propagate to all nodes without duplication
 */
class NetDet(val numberNodes: Int, val treeOrder: Int) {

    data class Node(val v:Int, var up: Node?, val down: MutableList<Node> = mutableListOf()) {
        override fun toString(): String {
            return "Node(v=$v, up=${up?.v}, down=${down.map { it.v }})"
        }

        fun toStringCompact(): String {
            return "<-${up?.v}-[${v}]-${down.map { it.v }.joinToString(",")}->"
        }
    }

    private var fail: Boolean = false
    private var rootNode: Node? = null
    private var nodeMap = mutableMapOf<Int, Node>()

    override fun toString(): String {
        if(fail) return "FAIL"
        return nodeMap.values.sortedBy { it.v }.joinToString("\n") { it.toStringCompact() }
    }

    fun meanEdges(): Double? = if(fail) null else nodeMap.values.map { it.down.size  + 1}.average()

    fun varianceEdges(): Double {
        if(fail) return Double.POSITIVE_INFINITY
        val mean = meanEdges()
        return nodeMap.values.map { (it.down.size + 1 - mean!!).let { d -> d * d } }.average()
    }

    fun fill() {
        if(numberNodes == 1) {
            rootNode = Node(0, null)
            nodeMap[0] = rootNode!!
            return
        }

        val depth = requiredDepth()

        var n = 0
        val currentRow = mutableListOf<Node>()
        var prevRow : List<Node>? = null
        (0 until depth).forEach {
            row ->

            val rowCapacity = ceil(treeOrder.toFloat().pow(row)).toInt()
            println("Fill row ${row} capacity ${rowCapacity}")
            while(currentRow.size < rowCapacity) {
                if (row == 0) {
                    assert(n == 0)
                    Node(n, null).let {
                        currentRow.add(it)
                        rootNode = it
                        nodeMap[n] = it
                    }
                } else {
                    assert(n > 0)
                    val upNodeIndex = (currentRow.size  * prevRow!!.size) / rowCapacity
                    val newNode = Node(n, prevRow!![upNodeIndex])
                    prevRow!![upNodeIndex].down.add(newNode)
                    currentRow.add(newNode)
                    nodeMap[n] = newNode
                }
                n += 1
            }
            prevRow = currentRow.toList()
            currentRow.clear()
        }

        val outerNodes = n
        val lastTop = prevRow!!.toList()
        val middleNodes = numberNodes - outerNodes*2
        println("Upper tree filled, outerNodes=${outerNodes}, middleNodes=${middleNodes}")

        n = numberNodes - 1
        currentRow.clear()
        prevRow = null
        (0 until depth).forEach {
            row ->

            val rowCapacity = ceil(treeOrder.toFloat().pow(row) ).toInt()
            while(currentRow.size < rowCapacity) {
                if (row == 0) {
                    assert(n == numberNodes-1)
                    Node(n, rootNode).let {
                        currentRow.add(it)
                        rootNode!!.up = it
                        nodeMap[n] = it
                    }
                } else {
                    assert(n < (numberNodes-1))
                    val upNodeIndex = (currentRow.size  * prevRow!!.size) / rowCapacity
                    val newNode = Node(n, prevRow!![upNodeIndex])
                    prevRow!![upNodeIndex].down.add(newNode)
                    currentRow.add(newNode)
                    nodeMap[n] = newNode
                }
                n -= 1
            }
            prevRow = currentRow.toList()
            currentRow.clear()
        }

        val lastBottom = prevRow!!.toList()
        val middleRowCapacity = (treeOrder.toFloat().pow(depth)).toInt()
        if(middleNodes > middleRowCapacity) {
            println("fail middleNodes = ${middleNodes}, middleRowCapacity=${middleRowCapacity}")
            fail = true
            nodeMap.clear()
            rootNode = null
            return
        }

        (0 until middleNodes).forEach {
            idx ->
            n = idx + outerNodes
            val outerNodeIndex = (idx * lastBottom.size) / middleRowCapacity
            val newNode = Node(n, lastTop[outerNodeIndex], mutableListOf( lastBottom[outerNodeIndex]))
            lastTop[outerNodeIndex].down.add(newNode)
            lastBottom[outerNodeIndex].down.add(newNode)
            nodeMap[n] = newNode
        }

        lastBottom.forEachIndexed {
            idx, bottomNode ->
            if(bottomNode.down.isEmpty()) {
                bottomNode.down.add(lastTop[idx])
                lastTop[idx].down.add(bottomNode)
            }
        }

        fail = false
    }

    fun requiredDepth() : Int {
        val treeDepth = invSumPower(numberNodes / 2).toInt()
        val middleRowCapacity = (treeOrder.toFloat().pow(treeDepth)).toInt()
        val middleNodes = numberNodes - (sumPower(treeDepth) * 2)
        return if(middleNodes > middleRowCapacity) treeDepth+1 else treeDepth
    }

    /**
     * Calculate the next nodes (from node `forNode`) to send a message to as part of a
     * flood fill started by node `start`
     *
     * For instance: flood(0, 3) is called on node 3 when processing a flood message
     * starting from node 0
     *
     * @param start the node the floodfill starts at
     * @param forNode the node we want next steps for
     * @return a set of nodes to send to next
     */
    fun flood(start: Int, forNode: Int): Set<Int> {
        val seen = mutableSetOf<Int>()
        return floodFrom(seen, start, forNode)
    }

    private fun floodFrom(seen: MutableSet<Int>, start: Int, forNode: Int, level: Int = 0): Set<Int> {
        //println("${".".repeat(level)}floodFrom ${start} => ${forNode} seen=${seen}")
        seen.add(start)
        val nextNodes = unseenNeighbours(seen, nodeMap[start]!!.v)
        if(start == forNode) {
            return nextNodes
        }

        val toFill = nextNodes.filter { !seen.contains(it)}
        seen.addAll(toFill)

        return toFill.flatMap {
                floodFrom(seen, it, forNode, level+1)
            }.toSet()
    }

    private fun unseenNeighbours(seen: MutableSet<Int>, start: Int): Set<Int> {
        val res = mutableSetOf<Int>()
        val upNode = nodeMap[start]?.up?.v
        if(upNode != null && !seen.contains(upNode)) res.add(upNode)
        val downNodes = (nodeMap[start]?.down?.map { it.v } ?: emptyList()).filter { !seen.contains(it) }
        res.addAll(downNodes)
        return res
    }

    private fun sumPower(n: Int) = ((1.0 - treeOrder.toFloat().pow(n))/(1.0 - treeOrder)).toInt()
    private fun invSumPower(s: Int) = log(1 + s * treeOrder.toFloat() - s, treeOrder.toFloat())

}