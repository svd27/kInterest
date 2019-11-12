package info.kinterest.datastores.hazelcast

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.json
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.client.config.ClientNetworkConfig
import com.hazelcast.client.config.ClientUserCodeDeploymentConfig
import com.hazelcast.config.GroupConfig
import com.hazelcast.core.*
import com.hazelcast.jet.Jet
import com.hazelcast.query.Predicate
import com.hazelcast.query.Predicates
import info.kinterest.datastore.*
import info.kinterest.datastore.IdGenerator
import info.kinterest.datastores.AbstractDatastore
import info.kinterest.entity.*
import info.kinterest.filter.*
import info.kinterest.functional.Try
import info.kinterest.functional.getOrElse
import info.kinterest.functional.suspended
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.StringReader
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class HazelcastConfig(name: String, config: Map<String, Any>) : DatastoreConfig(TYPE, name, config) {
    constructor(name: String, addresses: List<String>, group: String) : this(name, mapOf("addresses" to addresses, "group" to group))

    val addresses: List<String> by config
    val group: String by config

    companion object {
        const val TYPE = "hazelcast"
    }
}


@ExperimentalCoroutinesApi
class HazelcastDatastore(cfg: HazelcastConfig, events: EventManager) : AbstractDatastore(cfg, events) {
    private val log = KotlinLogging.logger { }
    override val name: String = cfg.name

    val clientConfig = ClientConfig().apply {
        setNetworkConfig(ClientNetworkConfig().apply {
            addresses = cfg.addresses
            //TODO: make this configurable
            setSmartRouting(false)
        })
        setGroupConfig(GroupConfig(cfg.group))
        setUserCodeDeploymentConfig(
                ClientUserCodeDeploymentConfig().setEnabled(true)
        )
    }

    val jet = Jet.newJetClient(clientConfig)
    val client: HazelcastInstance = jet.hazelcastInstance

    private val dbLock: Mutex = Mutex()
    private val collections = mutableMapOf<KIEntityMeta, IMap<Any, HazelcastJsonValue>>()
    private val metas: MutableMap<String, KIEntityMeta> = mutableMapOf()

    private fun collection(meta: KIEntityMeta): IMap<Any, HazelcastJsonValue> =
            collections.getOrElse(meta.baseMeta) { throw DatastoreUnknownType(meta, this) }

    init {
        GlobalScope.launch { ready() }
    }

    override fun getIdGenerator(meta: KIEntityMeta): IdGenerator<*> = run {
        val idType = meta.baseMeta.idType
        when (idType) {
            is LongPropertyMeta -> LongGenerator(meta.baseMeta.name)
            is ReferenceProperty -> when (idType.type) {
                UUID::class -> UUIDGenerator
                else -> throw DatastoreException(this, "unsupported type for autogenerate $idType")
            }
            else -> throw DatastoreException(this, "unsupported type for autogenerate $idType")
        }
    }

    inner class LongGenerator(name: String) : IdGenerator<Long> {
        val atomic = client.cpSubsystem.getAtomicLong(name)

        override fun next(): Long = atomic.andIncrement
    }

    override suspend fun register(meta: KIEntityMeta) {
        dbLock.withLock {
            metas[meta.name] = meta
            if (meta.idGenerated) {
                when (meta.idType) {
                    is LongPropertyMeta -> if (!idGenerators.containsKey(meta.baseMeta)) {
                        idGenerators[meta.baseMeta] = LongGenerator(meta.baseMeta.name)
                    }
                    else -> throw DatastoreException(this, "ids of type ${meta.idType} not supported")
                }
            }
            collections[meta.baseMeta] = client.getMap<Any, HazelcastJsonValue>(meta.baseMeta.name)
        }
    }

    override fun <ID : Any, E : KIEntity<ID>> retrieve(type: KIEntityMeta, vararg ids: ID): Try<Flow<E>> = retrieve(type, ids.asIterable())

    override fun <ID : Any, E : KIEntity<ID>> retrieve(type: KIEntityMeta, ids: Iterable<ID>): Try<Flow<E>> = Try {
        val collection = collections.getOrElse(type) { throw DatastoreException(this) }
        flow {
            collection.executeOnKeys(ids.toSet(), RetrieveType()).map {
                val meta = metas.getOrElse(it.value.toString()) { throw DatastoreException(this@HazelcastDatastore, "unknown type ${it.value}") }
                @Suppress("UNCHECKED_CAST")
                emit(meta.instance<ID>(this@HazelcastDatastore, it.key as ID) as E)
            }
        }
    }


    override fun <ID : Any, E : KITransientEntity<ID>, R : KIEntity<ID>> create(vararg entities: E): Try<Flow<R>> = create(entities.asIterable())

    private val klaxon = Klaxon()

    override fun <ID : Any, E : KITransientEntity<ID>, R : KIEntity<ID>> create(entities: Iterable<E>): Try<Flow<R>> = Try {
        val es = entities.toList()
        if (es.isEmpty()) listOf<R>().asFlow() else {
            val collection = collection(es[0]._meta)
            es.map {
                val kiEntityMeta = it._meta
                require(kiEntityMeta is KIEntityMetaJvm)
                val metaInfo: MetaInfo = kiEntityMeta.metaBlock
                val types = metaInfo.types
                val metaInfType = types.joinToString(separator = ",") { it.name }

                log.debug { "create meta: $kiEntityMeta\nmetainfo: $metaInfo\ntypes: $metaInfType" }
                val id = if (kiEntityMeta.idGenerated) {
                    idGenerator(kiEntityMeta).next()
                } else it._id!!
                val props = it.properties.filter { it.key != "id" }.map {
                    kiEntityMeta.properties.getOrElse(it.key) {
                        throw IllegalStateException("failed to find property ${it.key} in $kiEntityMeta")
                    } to it.value
                }
                val properties = props.filter { it.first != kiEntityMeta.idType && it.first !is RelationProperty }
                val rels = props.filter { it.first is RelationProperty }
                @Suppress("UNCHECKED_CAST")
                val outgoings = rels.filter { it.second != null }.map { (p, value) ->
                    when (value) {
                        is KIEntity<*> -> p to listOf(RelationTo(kiEntityMeta, p as RelationProperty, value._meta, value.id, value._store.name))
                        is Collection<*> -> p to (value as Collection<KIEntity<*>>).map { v -> RelationTo(kiEntityMeta, p as RelationProperty, v._meta, v.id, v._store.name) }
                        else -> null
                    }

                }.filterNotNull()
                val json = json {
                    val outgoingPairs = outgoings.map { (p, value) ->
                        p.name to array(value.map {
                            obj(
                                    "relation" to it.relation,
                                    "fromType" to it.fromType.name,
                                    "toType" to it.toType.name,
                                    "toId" to it.toId,
                                    "toDatastore" to it.toDatastore
                            )
                        })
                    }.asIterable()
                    val obj = obj(properties.map { it.first.name to it.second })
                    obj.set(METAINFO, obj(
                            KIEntityMeta.TYPEKEY to metaInfo.type.name,
                            KIEntityMeta.TYPESKEY to array(metaInfo.types.map { it.name }),
                            KIEntityMeta.RELATIONSKEY to
                                    obj(KIEntityMeta.OUTGOING to
                                            obj(outgoingPairs)))
                    )
                    obj
                }.apply { log.debug { "created json ${this.toJsonString(true, true)}" } }
                collection.put(id, HazelcastJsonValue(json.toJsonString(canonical = true)))
                @Suppress("UNCHECKED_CAST")
                kiEntityMeta.instance<ID>(this, id as ID) as R
            }.apply { events.entitiesCreated(this) }.asFlow()
        }
    }


    private fun idGenerator(meta: KIEntityMeta) =
            idGenerators.getOrElse(meta.baseMeta) { throw DatastoreException(this) }

    override suspend fun <ID : Any, E : KIEntity<ID>> delete(vararg entities: E): Try<Set<ID>> = delete(entities.asIterable())

    override suspend fun <ID : Any, E : KIEntity<ID>> delete(entities: Iterable<E>): Try<Set<ID>> = Try.suspended(GlobalScope) {
        val es = entities.toList()
        if (es.isEmpty()) return@suspended setOf<ID>()

        val meta = es[0]._meta
        val collection = collection(meta)
        es.map { collection.remove(it.id); it.id }.toSet().apply { events.entitiesDeleted(meta, this) }
    }

    @ImplicitReflectionSerializer
    override suspend fun getValues(type: KIEntityMeta, id: Any, props: Set<PropertyMeta>): Try<Collection<Pair<PropertyMeta, Any?>>> = Try.suspended(GlobalScope) {
        val collection = collection(type)

        val res = collection.submitToKey(id, FieldsGetter(props.map { it.name }.toSet())).await() as? String
        log.debug { "submit returned: $res" }
        val json = res?.let { klaxon.parseJsonObject(StringReader(it)) }
                ?: throw DatastoreException(this, "hazelcast returned no result")
        log.trace { "json ${json.toMap()}" }
        log.trace { "FieldGetters returned $res" }
        props.map { it to json.get(it.name) }
    }

    @ImplicitReflectionSerializer
    override suspend fun setValues(type: KIEntityMeta, id: Any, props: Map<PropertyMeta, Any?>): Try<Unit> = Try.suspended(GlobalScope) {
        val collection = collection(type)
        val json = JsonObject().apply {
            for ((prop, value) in props) {
                set(prop.name, value)
            }
        }
        val submit: ICompletableFuture<Any> = collection.submitToKey(id, FieldsSetter(json.toJsonString()))

        @Suppress("UNCHECKED_CAST")
        val res1 = submit.await() as? String
        val res: Map<String, Any?>? = res1?.let { klaxon.parse<Map<String, Any?>>(it) }
        res?.entries?.fold(listOf<Pair<PropertyMeta, Pair<Any?, Any?>>>()) { acc, (name, value) ->
            val propertyMeta = type.properties[name]!!
            acc + (propertyMeta to (value to props[propertyMeta]))
        }?.let { events.entityUpdated(type.instance(this, id), it) }

        Unit
    }

    override suspend fun <ID : Any, E : KIEntity<ID>> addRelations(type: KIEntityMeta, id: Any, prop: RelationProperty, entities: Collection<E>) {
        val collection = collection(type)
        val json = json {
            array(
                    entities.map {
                        json {
                            obj("toId" to it.id, "toType" to it._meta.name, "toDatastore" to it._store.name)
                        }
                    }
            )
        }
        val exec = AddRelations(prop.name, json.toJsonString())
        collection.submitToKey(id, exec).await()
    }

    override suspend fun <ID : Any, E : KIEntity<ID>> removeRelations(type: KIEntityMeta, id: Any, prop: RelationProperty, entities: Collection<E>) {
        val collection = collection(type)
        val json = json {
            array(
                    entities.map {
                        json {
                            obj("toId" to it.id, "toType" to it._meta.name, "toDatastore" to it._store.name)
                        }
                    }
            )
        }
        val exec = RemoveRelations(prop.name, json.toJsonString())
        collection.submitToKey(id, exec).await()
    }

    override suspend fun <ID : Any, E : KIEntity<ID>> setRelations(type: KIEntityMeta, id: Any, prop: RelationProperty, entities: Collection<E>) {
        val collection = collection(type)
        val json = json {
            array(
                    entities.map {
                        json {
                            obj("toId" to it.id, "toType" to it._meta.name, "toDatastore" to it._store.name)
                        }
                    }
            )
        }
        val exec = SetRelations(prop.name, json.toJsonString())
        collection.submitToKey(id, exec).await()
    }

    @UnstableDefault
    @ImplicitReflectionSerializer
    override fun <ID : Any, E : KIEntity<ID>> getRelations(type: KIEntityMeta, id: Any, prop: RelationProperty): Try<Flow<E>> = run {
        val collection = collection(type)

        val exec = GetRelations(prop.name)

        flow {
            val serialiser = prop.contained.idType.serializer()
            val out = collection.submitToKey(id, exec).await() as? HazelcastJsonValue
            val json = com.hazelcast.internal.json.Json.parse(out.toString()).asArray()

            log.trace { "json array: ${json.toList()}" }
            @Suppress("UNCHECKED_CAST")
            val ids = json.map { Json.parse(serialiser, it.asObject().get("toId").toString()) as? ID }.filterNotNull()
            log.trace { "retrieve ${prop.contained} with ids $ids" }
            retrieve<ID, E>(prop.contained, ids).getOrElse { throw it }.map {
                log.trace { "getRelations emits $it" }
                emit(it)
            }
        }

        val serialiser = prop.contained.idType.serializer()
        val out = runBlocking { collection.submitToKey(id, exec).await() as? HazelcastJsonValue }
        val json = com.hazelcast.internal.json.Json.parse(out.toString()).asArray()

        log.trace { "json array: ${json.toList()}" }
        @Suppress("UNCHECKED_CAST")
        val ids = json.map { Json.parse(serialiser, it.asObject().get("toId").toString()) as? ID }.filterNotNull()
        log.trace { "retrieve ${prop.contained} with ids $ids" }
        retrieve<ID, E>(prop.contained, ids)
    }


    override suspend fun addIncomingRelations(id: Any, relations: Collection<RelationFrom>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun setIncomingRelations(id: Any, relations: Collection<RelationFrom>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun removeIncomingRelations(id: Any, relations: Collection<RelationFrom>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <ID : Any, E : KIEntity<ID>> query(f: FilterWrapper<ID, E>): Try<Flow<E>> = Try {
        val collection = collection(f.meta)

        val typePredicate = Predicates.equal("$METAINFO_TYPES[any]", f.meta.name)
        val predicate = Predicates.and(typePredicate, f.predicate)
        log.debug { "query ${collection.name} predicate: $predicate" }
        flow<E> {
            collection.executeOnEntries(RetrieveType(), predicate).forEach {
                val meta = metas.getOrElse(it.value.toString()) {
                    throw DatastoreException(this@HazelcastDatastore, "unknown meta ${it.value} where type of returned object is ${it.value::class}")
                }
                @Suppress("UNCHECKED_CAST")
                emit(meta.instance<ID>(this@HazelcastDatastore, it.key) as E)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    val Filter<*, *>.predicate: Predicate<String, Any?>
        get() = run {
            when (this) {
                is FilterWrapper<*, *> -> f.predicate
                is LogicalFilter<*, *> -> when (this) {
                    is AndFilter<*, *> -> Predicates.and(*content.map { it.predicate }.toTypedArray()) as Predicate<String, Any?>
                    is OrFilter<*, *> -> Predicates.or(*content.map { it.predicate }.toTypedArray()) as Predicate<String, Any?>
                }
                is AllFilter<*, *> -> Predicates.alwaysTrue()
                is NoneFilter<*, *> -> Predicates.alwaysFalse()
                is PropertyFilter<*, *, *> -> when (this) {
                    is ComparisonFilter<*, *, *> -> when (this) {
                        is GTFilter<*, *, *> ->
                            Predicates.greaterThan(prop.name, value) as Predicate<String, Any?>
                        is LTFilter<*, *, *> -> Predicates.lessThan(prop.name, value) as Predicate<String, Any?>
                    }
                }
            }
        }

    private suspend fun <V> ICompletableFuture<V>.await(): V = suspendCoroutine {
        andThen(object : ExecutionCallback<V> {
            override fun onFailure(t: Throwable?) {
                it.resumeWithException(t ?: IllegalStateException())
            }

            override fun onResponse(response: V) {
                it.resume(response)
            }
        })
    }

    companion object {
        const val METAINFO: String = "_metaInfo"
        const val METAINFO_TYPE: String = "${METAINFO}.type"
        const val METAINFO_TYPES: String = "${METAINFO}.types"
    }
}