package info.kinterest.entity

import info.kinterest.functional.Try
import kotlinx.coroutines.runBlocking
import java.lang.IllegalStateException

interface KIEntityJvm<ID:Any> : KIEntity<ID> {
    override fun<V> getValue(prop:PropertyName) : V = runBlocking {
        _store.getValues(_meta, id, setOf(prop)).fold({throw it}) {
            Try {
                it.firstOrNull()?.second?:throw IllegalStateException("$prop not found in ${_meta.type} with id $id")
            }.fold({throw it}) {it} as V
        }
    }

    fun setValue(prop: PropertyName, value:Any?) = runBlocking {
        _store.setValues(_meta, id, mapOf(prop to value)).fold({throw it}) {Unit}
    }
}

interface KIEntityMetaJvm : KIEntityMeta {
    override val name get() = type.qualifiedName!!
}

