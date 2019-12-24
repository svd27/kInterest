package info.kinterest.jvm.backend.entity

import info.kinterest.entity.KIEntity
import info.kinterest.entity.PropertyMeta
import info.kinterest.entity.RelationProperty
import info.kinterest.functional.Try
import info.kinterest.functional.getOrElse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

interface KIEntityJvm<ID:Any> : KIEntity<ID> {
    @Suppress("UNCHECKED_CAST")
    override fun<V> getValue(property: PropertyMeta) : V = runBlocking {
        _store.getValues(_meta, id, setOf(property)).fold({throw it}) {
            Try {
                val pair = it.firstOrNull { it.first == property }?:throw IllegalStateException("no result for $property in ${_meta.name} with id $id")
                pair.second
            }.fold({throw it}) {it} as V
        }
    }

    override fun<V> setValue(property: PropertyMeta, v:V?) = runBlocking {
        _store.setValues(_meta, id, mapOf(property to v)).fold({throw it}) {Unit}
    }

    override fun <ID:Any, E:KIEntity<ID>> getRelations(property: RelationProperty): Collection<E> = runBlocking { _store.getRelations<Any, E>(_meta, id, property).getOrElse { throw it }.toList(mutableListOf()) }
}


