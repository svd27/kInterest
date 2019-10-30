package info.kinterest.filter

import info.kinterest.entity.KIEntity
import info.kinterest.entity.KIEntityMeta
import info.kinterest.entity.PropertyMeta

sealed class Filter<ID:Any,E:KIEntity<ID>>(val meta: KIEntityMeta) {
    abstract fun matches(e:E) : Boolean
    abstract fun inverse() : Filter<ID,E>

    operator fun not() : Filter<ID,E> = inverse()
    open fun and(f:Filter<ID,E>) : AndFilter<ID,E> = AndFilter(meta, listOf(this, f))
    open fun or(f:Filter<ID,E>) : OrFilter<ID,E> = OrFilter(meta, listOf(this, f))
}
sealed class LogicalFilter<ID:Any,E:KIEntity<ID>>(meta: KIEntityMeta) : Filter<ID,E>(meta)

sealed class LogicalCombinationFilter<ID:Any,E:KIEntity<ID>>(meta: KIEntityMeta) : LogicalFilter<ID,E>(meta) {
    abstract val content : List<Filter<ID,E>>
}
class AndFilter<ID:Any,E:KIEntity<ID>>(meta: KIEntityMeta, operands:Iterable<Filter<ID,E>>) : LogicalCombinationFilter<ID,E>(meta) {
    override val content : List<Filter<ID,E>>
    init {
        content = operands.flatMap {
            @Suppress("UNCHECKED_CAST")
            if(it is AndFilter<*,*>) it.content as List<Filter<ID,E>> else listOf(it)
        }
    }

    override fun matches(e: E): Boolean = content.all { it.matches(e) }

    override fun inverse(): Filter<ID, E> = OrFilter(meta, content.map { !it })

    @Suppress("UNCHECKED_CAST")
    override fun and(f: Filter<ID, E>): AndFilter<ID, E> =
            if(f is AndFilter<*,*>) AndFilter(meta, content+(f.content as Iterable<Filter<ID,E>>)) else
                AndFilter(meta, content+f)
}

class OrFilter<ID:Any,E:KIEntity<ID>>(meta: KIEntityMeta, operands:Iterable<Filter<ID,E>>) : LogicalFilter<ID,E>(meta) {
    val content : List<Filter<ID,E>>
    init {
        content = operands.flatMap {
            @Suppress("UNCHECKED_CAST")
            if(it is OrFilter<*,*>) it.content as List<Filter<ID,E>> else listOf(it)
        }
    }

    override fun matches(e: E): Boolean = content.any { it.matches(e) }

    override fun inverse(): Filter<ID, E> = AndFilter(meta, content.map { !it })

    @Suppress("UNCHECKED_CAST")
    override fun or(f: Filter<ID, E>): OrFilter<ID, E> = if(f is OrFilter<*,*>) OrFilter(meta, content+(f.content as Iterable<Filter<ID,E>>)) else
        OrFilter(meta, content+f)
}

sealed class PropertyFilter<ID:Any,E:KIEntity<ID>,V>(meta: KIEntityMeta, val prop : PropertyMeta) : Filter<ID,E>(meta)
sealed class ComparisonFilter<ID:Any,E:KIEntity<ID>,V>(meta: KIEntityMeta, prop: PropertyMeta, val value : Comparable<V>) : PropertyFilter<ID,E,V>(meta, prop) {
    abstract fun compare(v1:Comparable<V>,v2: V?) : Boolean

    override fun matches(e: E): Boolean = compare(value, e.getValue<V>(prop))
}
class GTFilter<ID:Any,E:KIEntity<ID>,V>(meta: KIEntityMeta, prop: PropertyMeta, value : Comparable<V>) : ComparisonFilter<ID,E,V>(meta, prop, value) {
    override fun inverse(): Filter<ID, E> = GTFilter(meta, prop, value)

    override fun compare(v1: Comparable<V>, v2: V?): Boolean = if(v2 == null) true else v1 <= v2
}

class LTFilter<ID:Any,E:KIEntity<ID>,V>(meta: KIEntityMeta, prop: PropertyMeta, value : Comparable<V>) : ComparisonFilter<ID,E,V>(meta, prop, value) {
    override fun inverse(): Filter<ID, E> = GTFilter(meta, prop, value)

    override fun compare(v1: Comparable<V>, v2: V?): Boolean = if(v2 == null) true else v1 >= v2
}


class FilterWrapper<ID:Any,E:KIEntity<ID>>(val f : Filter<ID,E>) : Filter<ID,E>(f.meta) {
    override fun matches(e: E): Boolean = f.matches(e)

    override fun inverse(): FilterWrapper<ID, E> = FilterWrapper(!f)

    class Builder<ID:Any,E:KIEntity<ID>>(val meta: KIEntityMeta) {
        var current : Filter<ID,E> = AllFilter<ID,E>(meta)
        fun build() : FilterWrapper<ID,E> = FilterWrapper(current)

        infix fun Filter<ID,E>.and(f:Filter<ID,E>)  : Filter<ID,E> = AndFilter(meta, listOf(this, f)).run { current=this; this }
        infix fun Filter<ID,E>.or(f:Filter<ID,E>)  : Filter<ID,E> = OrFilter(meta, listOf(this, f)).run { current=this; this }
        infix fun Int.gte(prop:String) : Filter<ID,E> = LTFilter<ID,E,Int>(meta, meta.properties[prop]!!, this as Comparable<Int>).run { current=this; this }
        infix fun Int.lte(prop:String) : Filter<ID,E> = GTFilter<ID,E,Int>(meta, meta.properties[prop]!!, this as Comparable<Int>).run { current=this; this }
    }
}

fun<ID:Any,E:KIEntity<ID>> filter(meta: KIEntityMeta, work:FilterWrapper.Builder<ID,E>.()->Unit) : FilterWrapper<ID,E> = FilterWrapper.Builder<ID,E>(meta).let {
    it.work()
    it.build()
}

class AllFilter<ID:Any,E:KIEntity<ID>>(meta: KIEntityMeta) : Filter<ID,E>(meta) {
    override fun matches(e: E): Boolean = true

    override fun inverse(): Filter<ID, E> = NoneFilter(meta)
}

class NoneFilter<ID:Any,E:KIEntity<ID>>(meta: KIEntityMeta) : Filter<ID,E>(meta) {
    override fun matches(e: E): Boolean = false

    override fun inverse(): Filter<ID, E> = AllFilter(meta)
}

