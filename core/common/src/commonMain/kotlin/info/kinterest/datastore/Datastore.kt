package info.kinterest.datastore

import info.kinterest.DONTDOTHIS
import info.kinterest.entity.*
import info.kinterest.filter.FilterWrapper
import info.kinterest.functional.Try
import kotlinx.coroutines.flow.Flow


interface Datastore {
    val name : String
    val instanceId : Any

    suspend fun register(meta: KIEntityMeta)

    fun<ID:Any,E:KIEntity<ID>> retrieve(type:KIEntityMeta, vararg ids:ID) : Try<Flow<E>>
    fun<ID:Any,E:KIEntity<ID>> retrieve(type:KIEntityMeta, ids:Iterable<ID>) : Try<Flow<E>>
    fun<ID:Any,E:KITransientEntity<ID>,R:KIEntity<ID>> create(vararg entities:E) : Try<Flow<R>>
    fun<ID:Any,E:KITransientEntity<ID>,R:KIEntity<ID>> create(entities:Iterable<E>) : Try<Flow<R>>
    suspend fun<ID:Any,E:KIEntity<ID>> delete(vararg entities: E) : Try<Set<ID>>
    suspend fun<ID:Any,E:KIEntity<ID>> delete(entities: Iterable<E>) : Try<Set<ID>>
    suspend fun getValues(type: KIEntityMeta, id:Any, props:Set<PropertyMeta>) : Try<Collection<Pair<PropertyMeta,Any?>>>
    suspend fun setValues(type: KIEntityMeta, id:Any, props:Map<PropertyMeta,Any?>) : Try<Unit>

    suspend fun<ID:Any,E:KIEntity<ID>> addRelations(type: KIEntityMeta, id:Any, prop:RelationProperty, entities:Collection<E>)
    suspend fun<ID:Any,E:KIEntity<ID>> setRelations(type: KIEntityMeta, id:Any, prop:RelationProperty, entities:Collection<E>)
    suspend fun<ID:Any,E:KIEntity<ID>> removeRelations(type: KIEntityMeta, id:Any, prop:RelationProperty, entities:Collection<E>)
    fun<ID:Any,E:KIEntity<ID>> getRelations(type: KIEntityMeta, id:Any, prop:RelationProperty) : Try<Flow<E>>

    suspend fun addIncomingRelations(id: Any, relations: Collection<RelationFrom>)
    suspend fun setIncomingRelations(id: Any, relations: Collection<RelationFrom>)
    suspend fun removeIncomingRelations(id: Any, relations: Collection<RelationFrom>)

    fun<ID:Any,E:KIEntity<ID>> query(f:FilterWrapper<ID,E>) : Try<Flow<E>>

    fun getIdGenerator(meta: KIEntityMeta) : IdGenerator<*>
}

interface IdGenerator<V:Any> {
    fun next() : V
}


object NOSTORE : Datastore {
    override val name: String = "NOSTORE"
    override val instanceId: Any = ""

    override suspend fun register(meta: KIEntityMeta) {}

    override fun <ID : Any, E : KIEntity<ID>> retrieve(type: KIEntityMeta, vararg ids: ID): Try<Flow<E>> = DONTDOTHIS()

    override fun <ID : Any, E : KIEntity<ID>> retrieve(type: KIEntityMeta, ids: Iterable<ID>): Try<Flow<E>> = DONTDOTHIS()

    override fun <ID : Any, E : KITransientEntity<ID>, R : KIEntity<ID>> create(vararg entities: E): Try<Flow<R>> = DONTDOTHIS()

    override fun <ID : Any, E : KITransientEntity<ID>, R : KIEntity<ID>> create(entities: Iterable<E>): Try<Flow<R>> = DONTDOTHIS()

    override suspend fun <ID : Any, E : KIEntity<ID>> delete(vararg entities: E): Try<Set<ID>> = DONTDOTHIS()
    override suspend fun <ID : Any, E : KIEntity<ID>> delete(entities: Iterable<E>): Try<Set<ID>> = DONTDOTHIS()

    override suspend fun getValues(type: KIEntityMeta, id: Any, props: Set<PropertyMeta>): Try<Collection<Pair<PropertyMeta, Any?>>> = DONTDOTHIS()
    override suspend fun setValues(type: KIEntityMeta, id: Any, props: Map<PropertyMeta, Any?>): Try<Unit> = DONTDOTHIS()

    override fun <ID : Any, E : KIEntity<ID>> query(f: FilterWrapper<ID, E>): Try<Flow<E>> = DONTDOTHIS()
    override suspend fun <ID : Any, E : KIEntity<ID>> addRelations(type: KIEntityMeta, id: Any, prop: RelationProperty, entities: Collection<E>) = DONTDOTHIS()
    override suspend fun <ID : Any, E : KIEntity<ID>> setRelations(type: KIEntityMeta, id: Any, prop: RelationProperty, entities: Collection<E>) = DONTDOTHIS()
    override suspend fun <ID : Any, E : KIEntity<ID>> removeRelations(type: KIEntityMeta, id: Any, prop: RelationProperty, entities: Collection<E>) = DONTDOTHIS()
    override fun <ID : Any, E : KIEntity<ID>> getRelations(type: KIEntityMeta, id: Any, prop: RelationProperty): Try<Flow<E>> = DONTDOTHIS()

    override suspend fun  addIncomingRelations(id: Any, relations: Collection<RelationFrom>) = DONTDOTHIS()
    override suspend fun  setIncomingRelations(id: Any, relations: Collection<RelationFrom>) = DONTDOTHIS()
    override suspend fun  removeIncomingRelations(id: Any, relations: Collection<RelationFrom>) = DONTDOTHIS()

    override fun getIdGenerator(meta: KIEntityMeta): IdGenerator<*> = DONTDOTHIS()
}