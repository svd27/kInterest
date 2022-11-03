package info.kinterest.jvm.backend.entity

import info.kinterest.entity.KIEntity
import info.kinterest.entity.RelationProperty
import info.kinterest.entity.RelationTo
import info.kinterest.entity.SingleRelationProperty
import info.kinterest.functional.getOrElse
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface RelationCollection<ID:Any, out E: KIEntity<ID>> : Collection<E> {
    val prop : RelationProperty
}

private class RelationSet<ID:Any, out E: KIEntity<ID>>(val e: KIEntity<Any>, override val prop: RelationProperty) : RelationCollection<ID, E>, AbstractSet<E>() {
    private val relations : Set<RelationTo> = setOf()
    override val size: Int
        get() = relations.size

    override fun iterator(): Iterator<E> = e._store.getRelations<ID,E>(e._meta, e.id, prop).getOrElse { throw it }.run { runBlocking { toList(mutableListOf()).iterator() }}

}

class MutableRelationSet<ID:Any, E: KIEntity<ID>>(val e: KIEntity<Any>, override val prop: RelationProperty) : RelationCollection<ID,E>, AbstractMutableSet<E>() {
    private val relations : MutableSet<RelationTo> = mutableSetOf()
    override val size: Int = relations.size

    override fun add(element: E): Boolean = runBlocking { e._store.addRelations(e._meta, e.id, prop, listOf(element)); true }

    override fun iterator(): MutableIterator<E> = e._store.getRelations<ID,E>(e._meta, e.id, prop).getOrElse { throw it }.run { runBlocking { toList(mutableListOf()).toMutableList().iterator() }}
}

class RelationList<ID:Any, out E: KIEntity<ID>>(val e: KIEntity<Any>, override val prop: RelationProperty) : RelationCollection<ID,E>, AbstractList<E>() {
    private val relations : List<RelationTo> = listOf()
    override val size: Int
        get() = relations.size

    override fun get(index: Int): E {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class MutableRelationList<ID:Any, E: KIEntity<ID>>(val e: KIEntity<Any>, override val prop: RelationProperty) : RelationCollection<ID,E>, AbstractMutableList<E>() {
    private val relations : List<RelationTo> = listOf()
    override val size: Int
        get() = relations.size

    override fun get(index: Int): E {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(index: Int, element: E) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeAt(index: Int): E {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun set(index: Int, element: E): E {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class RelationSetDelegate<E>(e: KIEntity<Any>, prop:RelationProperty) {
    private val set = RelationSet<Any, KIEntity<Any>>(e, prop)
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Set<E> = set as Set<E>
}

class RelationListDelegate<E>(e: KIEntity<Any>, prop:RelationProperty) {
    val list = RelationList<Any, KIEntity<Any>>(e,prop)
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): List<E> = list as List<E>
}
class MutableRelationSetDelegate<E>(e: KIEntity<Any>, prop:RelationProperty) {
    val set = MutableRelationSet<Any, KIEntity<Any>>(e, prop)
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableSet<E> = set as MutableSet<E>
}

class MutableRelationListDelegate<E>(e: KIEntity<Any>, prop:RelationProperty) {
    val list = MutableRelationList<Any, KIEntity<Any>>(e, prop)
    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableList<E> = list as MutableList<E>
}

class SingleRelationNullablePropertyDelegate<E>(private val e: KIEntity<Any>, private val prop: SingleRelationProperty) : ReadWriteProperty<Any,E?> {
    private val log = KotlinLogging.logger { }
    override fun getValue(thisRef: Any, property: KProperty<*>): E? = runBlocking {
        @Suppress("UNCHECKED_CAST")
        e._store.getRelations<Any, KIEntity<Any>>(e._meta, e.id, prop).getOrElse { throw it }.map {
            @Suppress("UNCHECKED_CAST")
            it as E
        }.toList(mutableListOf())
                .firstOrNull()
                .apply { log.debug { "${SingleRelationDelegate::class.qualifiedName} got $this" } }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: E?) {
        check(thisRef is KIEntity<*>)
        if(value==null) runBlocking { thisRef._store.setRelations(e._meta, e.id, prop, emptyList<KIEntity<Any>>()) }
        else runBlocking { thisRef._store.setRelations(e._meta, e.id, prop, listOf(value as KIEntity<Any>)) }
    }
}

class SingleRelationPropertyDelegate<E>(private val e: KIEntity<Any>, private val prop: SingleRelationProperty) : ReadWriteProperty<Any,E> {
    private val log = KotlinLogging.logger { }
    override fun getValue(thisRef: Any, property: KProperty<*>): E = runBlocking {
        @Suppress("UNCHECKED_CAST")
        e._store.getRelations<Any, KIEntity<Any>>(e._meta, e.id, prop).getOrElse { throw it }.map {
            @Suppress("UNCHECKED_CAST")
            it as E
        }.toList(mutableListOf())
                .first()
                .apply { log.debug { "${SingleRelationDelegate::class.qualifiedName} got $this" } }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: E) {
        check(thisRef is KIEntity<*>)
        if(value==null) runBlocking { thisRef._store.setRelations(e._meta, e.id, prop, listOf<KIEntity<Any>>()) }
        else runBlocking { thisRef._store.setRelations(e._meta, e.id, prop, listOf(value as KIEntity<Any>)) }
    }
}

class SingleRelationDelegate<E>(private val e: KIEntity<Any>, private val prop: SingleRelationProperty) {

    @Suppress("UNCHECKED_CAST")
    operator fun provideDelegate(thisRef: Any, property: KProperty<*>) : ReadWriteProperty<Any, E> =
            if(property.returnType.isMarkedNullable)
                SingleRelationNullablePropertyDelegate<E>(e, prop) as ReadWriteProperty<Any, E>
    else SingleRelationPropertyDelegate<E>(e, prop)


}