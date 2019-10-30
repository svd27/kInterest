package info.kinterest.entity

import info.kinterest.functional.Try
import kotlinx.coroutines.runBlocking

interface KIEntityJvm<ID:Any> : KIEntity<ID> {
    @Suppress("UNCHECKED_CAST")
    override fun<V> getValue(property:PropertyMeta) : V = runBlocking {
        _store.getValues(_meta, id, setOf(property)).fold({throw it}) {
            Try {
                it.firstOrNull()?.second?:throw IllegalStateException("$property not found in ${_meta.type} with id $id")
            }.fold({throw it}) {it} as V
        }
    }

    override fun<V> setValue(property: PropertyMeta, v:V?) = runBlocking {
        _store.setValues(_meta, id, mapOf(property to v)).fold({throw it}) {Unit}
    }
}

interface KIEntityMetaJvm : KIEntityMeta {
    override val name get() = type.qualifiedName!!
}

