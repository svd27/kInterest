package info.kinterest.datastore

import info.kinterest.DONTDOTHIS
import info.kinterest.entity.KIEntity
import info.kinterest.entity.KIEntityMeta
import info.kinterest.entity.KITransientEntity
import info.kinterest.entity.PropertyMeta
import info.kinterest.filter.FilterWrapper
import info.kinterest.functional.Try


interface Datastore {
    val name : String

    suspend fun register(meta: KIEntityMeta)

    suspend fun retrieve(type:KIEntityMeta, vararg ids:Any) : Try<Collection<KIEntity<Any>>>
    suspend fun retrieve(type:KIEntityMeta, ids:Iterable<Any>) : Try<Collection<KIEntity<Any>>>
    suspend fun<ID:Any,E:KITransientEntity<ID>> create(vararg entities:E) : Try<Collection<KIEntity<ID>>>
    suspend fun<ID:Any,E:KITransientEntity<ID>> create(entities:Iterable<E>) : Try<Collection<KIEntity<ID>>>
    suspend fun<ID:Any,E:KIEntity<ID>> delete(vararg entities: E) : Try<Set<ID>>
    suspend fun<ID:Any,E:KIEntity<ID>> delete(entities: Iterable<E>) : Try<Set<ID>>
    suspend fun getValues(type: KIEntityMeta, id:Any, props:Set<PropertyMeta>) : Try<Collection<Pair<PropertyMeta,Any?>>>
    suspend fun setValues(type: KIEntityMeta, id:Any, props:Map<PropertyMeta,Any?>) : Try<Unit>

    suspend fun<ID:Any,E:KIEntity<ID>> query(f:FilterWrapper<ID,E>) : Try<Iterable<E>>
}

object NOSTORE : Datastore {
    override val name: String = "NOSTORE"
    override suspend fun register(meta: KIEntityMeta) {}

    override suspend fun retrieve(type:KIEntityMeta, vararg ids:Any): Try<Collection<KIEntity<Any>>> = DONTDOTHIS()
    override suspend fun retrieve(type: KIEntityMeta, ids: Iterable<Any>): Try<Collection<KIEntity<Any>>> = DONTDOTHIS()

    override suspend fun <ID : Any, E : KITransientEntity<ID>> create(vararg entities: E): Try<Collection<KIEntity<ID>>> = DONTDOTHIS()
    override suspend fun <ID : Any, E : KITransientEntity<ID>> create(entities: Iterable<E>): Try<Collection<KIEntity<ID>>> = DONTDOTHIS()

    override suspend fun <ID : Any, E : KIEntity<ID>> delete(vararg entities: E): Try<Set<ID>> = DONTDOTHIS()
    override suspend fun <ID : Any, E : KIEntity<ID>> delete(entities: Iterable<E>): Try<Set<ID>> = DONTDOTHIS()

    override suspend fun getValues(type: KIEntityMeta, id: Any, props: Set<PropertyMeta>): Try<Collection<Pair<PropertyMeta, Any?>>> = DONTDOTHIS()
    override suspend fun setValues(type: KIEntityMeta, id: Any, props: Map<PropertyMeta, Any?>): Try<Unit> = DONTDOTHIS()

    override suspend fun <ID : Any, E : KIEntity<ID>> query(f: FilterWrapper<ID, E>): Try<Iterable<E>> = DONTDOTHIS()
}