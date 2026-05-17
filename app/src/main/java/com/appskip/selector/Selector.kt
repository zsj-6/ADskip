package com.appskip.selector

import android.view.accessibility.AccessibilityNodeInfo

/**
 * CSS 风格的无障碍节点选择器，仿 GKD selector 引擎。
 *
 * 支持格式: [@]Type[attr=value][attr^=value][attr*=value][attr~=value]
 *
 * - @  标记目标节点（要点击的那个）
 * - >  父子/祖先后代关系
 * - +  兄弟关系（同一父节点下相邻）
 * - ,  多个选择器并列（命中任一即可）
 *
 * 示例:
 *   "@TextView[text*='跳过']"
 *   "FrameLayout > @TextView[text='跳过广告']"
 *   "TextView[text='广告'] + @TextView[text*='跳过']"
 */
class Selector(private val raw: String) {

    data class Condition(
        val attr: Attr,
        val op: Op,
        val value: String,
    )

    enum class Attr { TEXT, VID, DESC, CLASS_NAME, CLICKABLE, INDEX, DEPTH }
    enum class Op { EQ, PREFIX, CONTAINS, REGEX }

    data class Node(
        val conditions: List<Condition> = emptyList(),
        val isTarget: Boolean = false,
    )

    /** 选择器链，例如 [TextView{target}, ImageView] */
    data class Chain(
        val nodes: List<Node>,
        val relations: List<Relation>,
    ) {
        enum class Relation { DESCENDANT, SIBLING }
    }

    /** 解析后的多条链（逗号分隔的不同匹配方式） */
    val chains: List<Chain> = parseChains(raw)

    private fun parseChains(input: String): List<Chain> {
        return input.split(",").map { part -> parseChain(part.trim()) }
    }

    private fun parseChain(input: String): Chain {
        // 按 > 和 + 切分，保留分隔符
        val parts = mutableListOf<String>()
        val rels = mutableListOf<Chain.Relation>()
        var current = StringBuilder()
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch == '>' || ch == '+') {
                if (current.isNotBlank()) {
                    parts.add(current.toString().trim())
                    current = StringBuilder()
                }
                rels.add(if (ch == '>') Chain.Relation.DESCENDANT else Chain.Relation.SIBLING)
                i++
                // 跳过空格
                while (i < input.length && input[i] == ' ') i++
            } else if (ch == '[') {
                // 属性部分，找到匹配的 ]
                var depth = 1
                current.append(ch)
                i++
                while (i < input.length && depth > 0) {
                    if (input[i] == '[') depth++
                    else if (input[i] == ']') depth--
                    current.append(input[i])
                    i++
                }
            } else {
                current.append(ch)
                i++
            }
        }
        if (current.isNotBlank()) {
            parts.add(current.toString().trim())
        }

        val nodes = parts.map { parseNode(it) }
        return Chain(nodes, rels)
    }

    private fun parseNode(input: String): Node {
        var s = input.trim()
        val isTarget = s.startsWith("@")
        if (isTarget) s = s.substring(1)

        // 提取 Type 和条件
        val bracketIdx = s.indexOf('[')
        val typeName = if (bracketIdx > 0) s.substring(0, bracketIdx).trim() else s.trim()
        val conditionsStr = if (bracketIdx > 0) s.substring(bracketIdx) else ""

        val conditions = parseConditions(conditionsStr)

        // 如果指定了 className，添加为条件
        val allConditions = if (typeName.isNotEmpty() && typeName != "*") {
            listOf(Condition(Attr.CLASS_NAME, Op.CONTAINS, typeName)) + conditions
        } else {
            conditions
        }

        return Node(allConditions, isTarget)
    }

    private fun parseConditions(input: String): List<Condition> {
        val result = mutableListOf<Condition>()
        if (input.isBlank()) return result

        // 匹配 [attr op 'value'] 或 [attr op "value"]
        val regex = Regex("""\[(\w+)(\^=|\*=|\~=|=)['"](.+?)['"]\]""")
        for (match in regex.findAll(input)) {
            val attrName = match.groupValues[1]
            val opStr = match.groupValues[2]
            val value = match.groupValues[3]

            val attr = when (attrName.lowercase()) {
                "text" -> Attr.TEXT
                "vid", "viewid", "id" -> Attr.VID
                "desc", "description", "contentdescription" -> Attr.DESC
                "classname", "class", "cls" -> Attr.CLASS_NAME
                "clickable" -> Attr.CLICKABLE
                "index" -> Attr.INDEX
                "depth" -> Attr.DEPTH
                else -> continue
            }

            val op = when (opStr) {
                "=" -> Op.EQ
                "^=" -> Op.PREFIX
                "*=" -> Op.CONTAINS
                "~=" -> Op.REGEX
                else -> continue
            }

            result.add(Condition(attr, op, value))
        }
        return result
    }

    /**
     * 在当前节点树中查找匹配的第一个目标节点。
     * 返回 Pair<目标节点, 匹配链中的第一个节点> 用于调试。
     */
    fun find(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (chain in chains) {
            val result = matchChain(root, chain, 0)
            if (result != null) return result
        }
        return null
    }

    private fun matchChain(
        root: AccessibilityNodeInfo,
        chain: Chain,
        depth: Int,
    ): AccessibilityNodeInfo? {
        if (depth > 50) return null
        if (chain.nodes.isEmpty()) return null

        if (chain.nodes.size == 1) {
            // 单个节点选择器：在整个树中搜索
            val node = chain.nodes[0]
            val found = searchNode(root, node, 0)
            return if (node.isTarget || found != null) found else null
        }

        // 链式匹配：找第一个节点，然后沿着关系找下一个
        val firstNode = chain.nodes[0]
        val anchors = findAllMatching(root, firstNode, mutableListOf())

        for (anchor in anchors) {
            var current: AccessibilityNodeInfo? = anchor
            var matched = true

            for (i in 0 until chain.relations.size) {
                val nextNode = chain.nodes[i + 1]
                val relation = chain.relations[i]
                val next = findRelated(current!!, nextNode, relation)
                if (next != null) {
                    current?.let { if (it != next) it.recycle() }
                    current = next
                } else {
                    matched = false
                    break
                }
            }

            // 如果整条链匹配成功，且链中某个节点标记为 target，返回那个节点
            if (matched && current != null) {
                val targetIdx = chain.nodes.indexOfLast { it.isTarget }
                if (targetIdx >= 0) {
                    // 目标节点已经在 current 中（链的最后一个节点应该就是目标）
                    // 返回 current
                    return current
                } else {
                    return current
                }
            }
            current?.recycle()
            anchor.recycle()
        }

        return null
    }

    private fun findAllMatching(
        node: AccessibilityNodeInfo,
        target: Node,
        results: MutableList<AccessibilityNodeInfo>,
    ): List<AccessibilityNodeInfo> {
        if (matchesNode(node, target)) {
            results.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllMatching(child, target, results)
            child.recycle()
        }
        return results
    }

    private fun findRelated(
        anchor: AccessibilityNodeInfo,
        target: Node,
        relation: Chain.Relation,
    ): AccessibilityNodeInfo? {
        return when (relation) {
            Chain.Relation.DESCENDANT -> {
                // 在 anchor 的子孙中找 target
                searchNode(anchor, target, 0)
            }
            Chain.Relation.SIBLING -> {
                // 在 anchor 的兄弟中找 target
                val parent = anchor.parent ?: return null
                try {
                    val childCount = parent.childCount
                    val anchorIdx = (0 until childCount).firstOrNull { i ->
                        val child = parent.getChild(i)
                        child != null && child == anchor
                    } ?: -1

                    if (anchorIdx < 0) return null

                    // 向后搜索兄弟
                    for (i in anchorIdx + 1 until childCount) {
                        val sibling = parent.getChild(i) ?: continue
                        val found = searchNode(sibling, target, 0)
                        sibling.recycle()
                        if (found != null) return found
                    }
                    null
                } finally {
                    parent.recycle()
                }
            }
        }
    }

    private fun searchNode(
        node: AccessibilityNodeInfo,
        target: Node,
        depth: Int,
    ): AccessibilityNodeInfo? {
        if (depth > 50) return null
        if (matchesNode(node, target)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = searchNode(child, target, depth + 1)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun matchesNode(node: AccessibilityNodeInfo, target: Node): Boolean {
        for (cond in target.conditions) {
            val actual = when (cond.attr) {
                Attr.TEXT -> node.text?.toString() ?: ""
                Attr.VID -> node.viewIdResourceName ?: ""
                Attr.DESC -> node.contentDescription?.toString() ?: ""
                Attr.CLASS_NAME -> node.className?.toString() ?: ""
                Attr.CLICKABLE -> node.isClickable.toString()
                Attr.INDEX -> (-1).toString() // index 需要在上下文中匹配，暂不支持
                Attr.DEPTH -> (-1).toString()
            }

            val match = when (cond.op) {
                Op.EQ -> actual.equals(cond.value, ignoreCase = true)
                Op.PREFIX -> actual.startsWith(cond.value, ignoreCase = true)
                Op.CONTAINS -> actual.contains(cond.value, ignoreCase = true)
                Op.REGEX -> Regex(cond.value, RegexOption.IGNORE_CASE).containsMatchIn(actual)
            }

            if (!match) return false
        }
        return true
    }

    override fun toString(): String = raw
}
