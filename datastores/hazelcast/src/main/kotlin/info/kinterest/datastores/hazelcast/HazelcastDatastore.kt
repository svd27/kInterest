package info.kinterest.datastores.hazelcast

import com.beust.klaxon.Klaxon
import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.client.config.ClientNetworkConfig
import com.hazelcast.config.GroupConfig
import com.hazelcast.core.HazelcastJsonValue
import com.hazelcast.core.IMap
import com.hazelcast.query.Predicate
import com.hazelcast.query.Predicates
import info.kinterest.datastore.*
import info.kinterest.datastores.AbstractDatastore
import info.kinterest.datastores.IdGenerator
import info.kinterest.entity.*
import info.kinterest.filter.*
import info.kinterest.functional.Try
import info.kinterest.functional.suspended
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging

class HazelcastConfig(name: String, config : Map<String,Any>) : DatastoreConfig(TYPE, name, config) {
    constructor(name:String, addresses : List<String>, group: String) : this(name, mapOf("addresses" to addresses, "group" to group))

    val addresses : List<String> by config
    val group : String by config

    companion object {
        const val TYPE = "hazelcast"
    }
}


@ExperimentalCoroutinesApi
class HazelcastDatastore(cfg:HazelcastConfig, events:EventManager) : AbstractDatastore(cfg, events) {
    val log = KotlinLogging.logger {  }
    override val name: String = cfg.name

    val client = HazelcastClient.newHazelcastClient(ClientConfig().setNetworkConfig(ClientNetworkConfig().apply {
        addresses = cfg.addresses
        //TODO: make this configurable
        setSmartRouting(false)
    }).setGroupConfig(GroupConfig(cfg.group)))
    val dbLock : Mutex = Mutex()
    val collections = mutableMapOf<KIEntityMeta, IMap<Any,HazelcastJsonValue>>()
    val metas : MutableMap<String,KIEntityMeta> = mutableMapOf()

    fun collection(meta: KIEntityMeta) : IMap<Any,HazelcastJsonValue> =
            collections.getOrElse(meta.baseMeta) {throw DatastoreUnknownType(meta, this)}

    init {
        GlobalScope.launch { ready() }
    }

    inner class LongGenerator(name: String) : IdGenerator<Long> {
        val atomic = client.cpSubsystem.getAtomicLong(name)

        override fun next(): Long = atomic.andIncrement
    }

    override suspend fun register(meta: KIEntityMeta) {
        val name = meta.baseMeta.type.qualifiedName
        require(name!=null)
        dbLock.withLock {
            metas[meta.name] = meta
            if(meta.idGenerated) {
                when(meta.idType) {
                    is LongPropertyMeta -> if(!idGenerators.containsKey(meta.baseMeta)) {
                        idGenerators[meta.baseMeta] = LongGenerator(meta.baseMeta.type.qualifiedName!!)
                    }
                    else -> throw DatastoreException(this, "ids of type ${meta.idType} not supported")
                }
            }
            collections[meta.baseMeta] = client.getMap<Any,HazelcastJsonValue>("${this.name}name")
        }
    }

    override suspend fun retrieve(type: KIEntityMeta, vararg ids: Any): Try<Collection<KIEntity<Any>>> = retrieve(type, ids.asIterable())

    override suspend fun retrieve(type: KIEntityMeta, ids: Iterable<Any>): Try<Collection<KIEntity<Any>>> = Try.suspended(GlobalScope) {
        val collection = collections.getOrElse(type) {throw DatastoreException(this)}
        collection.getAll(ids.toSet()).map { deserialise<KIEntity<Any>,Any>(it.key, it.value) }
    }

    override suspend fun <ID : Any, E : KITransientEntity<ID>> create(vararg entities: E): Try<Collection<KIEntity<ID>>> =create(entities.asIterable())

    private val klaxon = Klaxon()

    override suspend fun <ID : Any, E : KITransientEntity<ID>> create(entities: Iterable<E>): Try<Collection<KIEntity<ID>>> = Try.suspended {
        val es = entities.toList()
        if(es.isEmpty()) return@suspended listOf<KIEntity<ID>>()
        val meta = es[0]._meta
        val collection = collection(meta)
        es.map {
            val kiEntityMeta = it._meta
            require(kiEntityMeta is KIEntityMetaJvm)
            val metaInfo = kiEntityMeta.metaBlock
            val types = metaInfo.types
            val metaInfType = types.joinToString(separator = ",") { it.name }
            val metaMap = mapOf(KIEntityMeta.TYPEKEY to metaInfo.type.name, KIEntityMeta.TYPESKEY to metaInfo.types.map { it.name }, KIEntityMeta.RELATIONSKEY to metaInfo.relations)
            log.debug { "create meta: $kiEntityMeta\nmetainfo: $metaInfo\ntypes: $metaInfType" }
            val id = if(kiEntityMeta.idGenerated) {
                idGenerator(kiEntityMeta).next()
            } else it._id!!
            val properties = it.properties.filter { it.key != "id" } + (METAINFO to metaMap) + (METAINFO_TYPE to metaInfType)
            log.debug { "create json: ${klaxon.toJsonString(properties)}" }
            collection.put(id, HazelcastJsonValue(klaxon.toJsonString(
                    //TODO: the filter should not be neccessary
                    properties.filter { it.value!=null }
            ).apply { log.trace { "creating json $this" } }))
            @Suppress("UNCHECKED_CAST")
            kiEntityMeta.instance<ID>(this, id as ID)
        }.apply { events.entitiesCreated(this) }
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

    override suspend fun getValues(type: KIEntityMeta, id: Any, props: Set<PropertyMeta>): Try<Collection<Pair<PropertyMeta, Any?>>> = Try.suspended(GlobalScope) {
        val collection = collection(type)
        val entity = collection.get(id)?:throw DatastoreKeyNotFound(type, id, this)
        val map = klaxon.parse<Map<String,Any?>>(entity.toString())?: mapOf()
        log.debug {
            "getValues json: ${map} ${map::class}"
        }
        props.map { it to map.get(it.name)}
    }

    override suspend fun setValues(type: KIEntityMeta, id: Any, props: Map<PropertyMeta, Any?>): Try<Unit> = Try.suspended(GlobalScope) {
        val collection = collection(type)
        val json = collection.get(id)?:throw DatastoreKeyNotFound(type, id, this)
        val entity = klaxon.parse<MutableMap<String,Any?>>(json.toString())!!
        val upds = props.map { (property, value) ->
            val old = entity[property.name]
            entity[property.name] = value
            property to (old to value)
        }
        collection.put(id, HazelcastJsonValue(klaxon.toJsonString(entity)))
        events.entityUpdated<Any, KIEntity<Any>>(type.instance(this, id), upds)
        Unit
    }

    override fun <ID : Any, E : KIEntity<ID>> query(f: FilterWrapper<ID, E>): Try<Flow<E>> = Try {
        val collection = collection(f.meta)
        val typePredicate = Predicates.ilike(METAINFO_TYPE, "%${f.meta.name}%")
        val predicate = Predicates.and(typePredicate, f.predicate)
        log.debug { "query ${collection.name} predicate: $predicate" }
        flow<E> {
            collection.entrySet(predicate).forEach {
                emit(deserialise(it.key, it.value))
            }.apply { log.trace { "returning $this" } }
        }
    }

    private fun <E : KIEntity<ID>, ID : Any> deserialise(id:Any, value : HazelcastJsonValue): E {
        val map = klaxon.parse<Map<String, Any?>>(value.toString())
        log.trace { "retrieved $map" }
        @Suppress("UNCHECKED_CAST")
        val metaEntry =
                map?.getOrElse(METAINFO) { throw DatastoreException(this, "no ${METAINFO} found in $map") } as? Map<String, Any>
                ?: throw DatastoreException(this, "$METAINFO is not a map")
        log.trace { "metaEntry $metaEntry" }
        val metaName = metaEntry.getOrElse(KIEntityMeta.TYPEKEY) { throw DatastoreException(this, "no ${KIEntityMeta.TYPEKEY} found in $metaEntry") }
        val meta = metas.getOrElse(metaName.toString()) { throw DatastoreException(this, "unknown meta $metaName") }
        log.trace { "$metaName returned $meta with name ${meta.name}" }

        @Suppress("UNCHECKED_CAST")
        return (@Suppress("UNCHECKED_CAST")
        meta.instance<ID>(this, id) as E).apply {
            log.trace { "returning $this" }
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

    companion object {
        const val METAINFO : String = "_metaInfo"
        const val METAINFO_TYPE : String = "_metaInfo-type"
    }
}