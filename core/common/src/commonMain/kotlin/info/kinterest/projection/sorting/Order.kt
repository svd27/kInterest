package info.kinterest.projection.sorting

import info.kinterest.entity.KIEntity
import info.kinterest.entity.PropertyMeta

fun PropertyMeta.asc(): OrderDef = OrderDef(this, OrderDirection.ASCENDING)
fun PropertyMeta.desc(): OrderDef = OrderDef(this, OrderDirection.DESCENDING)

enum class OrderDirection {
    ASCENDING,
    DESCENDING
}

enum class NullPlacement {
    NULL_LAST,
    NULLFIRST
}

data class OrderDef(val prop: PropertyMeta, val direction: OrderDirection = OrderDirection.ASCENDING)

data class Ordering<out K : Any, E : KIEntity<K>>(private val order: Iterable<OrderDef>, private val nullPlacement: NullPlacement = NullPlacement.NULL_LAST) : Comparator<E> {
    val comparator: Comparator<in E> = this
    @Suppress("UNCHECKED_CAST")
    override fun compare(a: E, b: E): Int {
        if (this === NATURAL) return 0
        for (o in order) {
            val va = a.getValue(o.prop) as Comparable<Any>?
            val vb = b.getValue(o.prop) as Comparable<Any>?
            if (va == null) return if (nullPlacement == NullPlacement.NULL_LAST) 1 else -1
            if (vb == null) return if (nullPlacement == NullPlacement.NULL_LAST) -1 else 1
            var res = va.compareTo(vb)
            if (o.direction == OrderDirection.DESCENDING) res *= -1
            if (res != 0) return res
        }
        return 0
    }

    fun isIn(e: E, range: Pair<E?, E?>) = range.first?.let {
        compare(e, it)
    } ?: 1 >= 0 && range.second?.let {
        compare(e, it)
    } ?: -1 < 0

    @Suppress("UNCHECKED_CAST")
    val mapComparator = object : Comparator<Map<String, Any?>> {
        override fun compare(a: Map<String, Any?>, b: Map<String, Any?>): Int {
            if (this === NATURAL) return 0
            for (o in order) {
                val va = a[o.prop.name] as Comparable<Any>?
                val vb = b[o.prop.name] as Comparable<Any>?
                if (va == null) return if (nullPlacement == NullPlacement.NULL_LAST) 1 else -1
                if (vb == null) return if (nullPlacement == NullPlacement.NULL_LAST) -1 else 1
                var res = va.compareTo(vb)
                if (o.direction == OrderDirection.DESCENDING) res *= -1
                if (res != 0) return res
            }
            return 0
        }
    }

    companion object {
        val NATURAL = Ordering<Nothing, Nothing>(listOf())
        fun <E : KIEntity<K>, K : Any> natural(): Ordering<K, E> = Ordering(listOf())
    }
}