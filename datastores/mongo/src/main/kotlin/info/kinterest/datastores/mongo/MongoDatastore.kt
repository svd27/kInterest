package info.kinterest.datastores.mongo

import com.mongodb.TransactionOptions
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Projections.include
import com.mongodb.reactivestreams.client.ClientSession
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import com.mongodb.reactivestreams.client.MongoDatabase
import info.kinterest.datastore.DatastoreException
import info.kinterest.datastore.DatastoreUnknownType
import info.kinterest.datastore.EventManager
import info.kinterest.datastores.AbstractDatastore
import info.kinterest.datastores.IdGenerator
import info.kinterest.entity.KIEntity
import info.kinterest.entity.KIEntityMeta
import info.kinterest.entity.KITransientEntity
import info.kinterest.entity.PropertyName
import info.kinterest.filter.*
import info.kinterest.functional.Try
import info.kinterest.functional.suspended
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.bson.Document
import org.bson.types.ObjectId


@ExperimentalCoroutinesApi
class MongoDatastore(cfg: MongodatastoreConfig, events: EventManager) : AbstractDatastore(cfg, events) {
    val log = KotlinLogging.logger {  }
    override val name: String = cfg.name
    internal val mongoClient = MongoClients.create(cfg.asConnectionString().apply { log.debug { "client on $this" } })
    internal val db: MongoDatabase

    inner class LongGenerator(val name: String) : IdGenerator<Long> {
        val collection: MongoCollection<Document> = db.getCollection("counters")

        init {
            runBlocking {
                val counter: Document? = collection.find(Document("_id", name)).awaitFirstOrNull()
                if (counter == null) {
                    collection.insertOne(Document("_id", name).append("counter", 0L)).awaitLast()
                }
            }
        }

        override fun next(): Long = runBlocking {
            val counter = collection.findOneAndUpdate(Document("_id", name), Document("\$inc", mutableMapOf("counter" to 1L))).awaitLast()["counter"]
            require(counter is Long)
            counter as? Long
                    ?: throw DatastoreException(this@MongoDatastore, "bad type of counter expected Long but found ${counter::class}")
        }
    }


    val dbLock: Mutex = Mutex()
    val collections: MutableMap<KIEntityMeta, MongoCollection<Document>> = mutableMapOf()
    val metas : MutableMap<String,KIEntityMeta> = mutableMapOf()

    init {
        //(log.underlyingLogger as ch.qos.logback.classic.Logger).level = Level.TRACE
        db = runBlocking {
            dbLock.withLock {
                val db = mongoClient.getDatabase(name)
                if (db.getCollection("counters") == null) {
                    db.createCollection("counters").awaitLast()
                }

                db
            }
        }
        GlobalScope.launch {
            ready()
        }
    }

    override suspend fun register(meta: KIEntityMeta) {
        val name = meta.baseMeta.type.qualifiedName
        require(name != null)
        dbLock.withLock {
            if (db.getCollection(name) == null) {
                db.createCollection(name).awaitLast()
            }
            collections[meta] = db.getCollection(name)
            metas[meta.name] = meta
            if (meta.idGenerated) {
                if (meta.idType != ObjectId::class) {
                    when (meta.idType) {
                        Long::class -> idGenerators.getOrPut(meta.baseMeta, { LongGenerator(meta.baseMeta.type.qualifiedName!!) })
                        else -> throw DatastoreException(this, "unsupported type for autogenerate ${meta.idType}")
                    }
                }
            }
        }
    }

    fun getCollection(meta: KIEntityMeta) : MongoCollection<Document> = collections.getOrElse(meta.baseMeta) {
        throw DatastoreUnknownType(meta, this)
    }

    fun getMeta(name: String) : KIEntityMeta = metas.getOrElse(name) {throw DatastoreException(this, "unknown meta $name")}

    override suspend fun retrieve(type: KIEntityMeta, vararg ids: Any): Try<Collection<KIEntity<Any>>> = retrieve(type, ids.asIterable())

    override suspend fun retrieve(type: KIEntityMeta, ids: Iterable<Any>): Try<Collection<KIEntity<Any>>> = Try.suspended(GlobalScope) {
        log.trace { "retrieve $type $ids" }
        val collection = getCollection(type)
        collection.find(`in`("_id", ids)).asFlow().map {
            log.trace { "retrieve $it" }
            type.instance<Any>(this, it["_id"]!!)
        }.toList()
    }

    override suspend fun <ID : Any, E : KITransientEntity<ID>> create(vararg entities: E): Try<Collection<KIEntity<ID>>> = create(entities.asIterable())

    override suspend fun <ID : Any, E : KITransientEntity<ID>> create(entities: Iterable<E>): Try<Collection<KIEntity<ID>>> = Try.suspended(GlobalScope) {
        val es = entities.toList()
        val collection = if (es.isNotEmpty()) {
            val _meta = es[0]._meta
            getCollection(_meta)
        } else return@suspended listOf<KIEntity<ID>>()
        val _meta = es[0]._meta

        val includeId = if (_meta.idGenerated) {
            if (_meta.idType != ObjectId::class) {
                val gen = idGenerators.getOrElse(_meta) { throw DatastoreException(this, "no generator found for $_meta") }
                es.forEach { it._id = gen.next() as ID }
                true
            } else false
        } else true
        val docs: List<Document> = es.fold(listOf()) { acc, e ->
            acc + e.properties.entries.fold(Document()) { doc, p ->
                doc.append(p.key, p.value);
            }.also {
                if (includeId) it.append("_id", e._id)
                val metaInfo = e._meta.initMetaBlock()
                it.append(METAKEY, metaInfo.asMap())
                log.trace { "document $it" }
            }
        }

        collection.insertMany(docs).awaitLast()
        log.trace { "docs: $docs" }
        docs.map { it.get("_id") }.filterNotNull().map { _meta.instance<ID>(this, it) }.apply {
            events.entitiesCreated(this)
        }
    }

    override suspend fun <ID : Any, E : KIEntity<ID>> delete(vararg entities: E): Try<Set<ID>> = delete(entities.asIterable())

    override suspend fun <ID : Any, E : KIEntity<ID>> delete(entities: Iterable<E>): Try<Set<ID>> = Try.suspended(GlobalScope) {
        val es = entities.toList()
        if (es.isEmpty()) return@suspended setOf<ID>()
        val type = es[0]._meta
        val collection = getCollection(type)

        val ids: Set<ID> = es.map { it.id as ID }.toSet()
        val result = collection.deleteMany(`in`("_id", ids)).awaitLast()
        if (result.deletedCount == es.size.toLong()) ids else {
            retrieve(type, ids).map {
                it.fold(ids) { acc, e ->
                    acc - (e.id as ID)
                }
            }.fold({ throw it }) { it }
        }.apply { events.entitiesDeleted(type, this) }
    }

    override suspend fun getValues(type: KIEntityMeta, id: Any, props: Set<PropertyName>): Try<Collection<Pair<PropertyName, Any?>>> = Try.suspended(GlobalScope) {
        val coll = getCollection(type)
        val doc = coll.find(Document("_id", id)).projection(include(props.map { it.name })).awaitLast()
        props.map { name -> name to doc[name.name] }
    }

    override suspend fun setValues(type: KIEntityMeta, id: Any, props: Map<PropertyName, Any?>): Try<Unit> = Try.suspended(GlobalScope) {
        val coll = getCollection(type)
        val txCfg = TransactionOptions.builder().build()
        if (props.isEmpty()) return@suspended

        val old = coll.find(Document("_id", id)).projection(include(props.map { (name, _) -> name.name })).awaitFirstOrNull()
        if (old == null) return@suspended
        val effective = props.filter { (name, value) -> old[name.name] != value }.map { (key, value) -> key.name to value }

        val upd = Document("\$set", effective.toMap())
        val updated = coll.updateOne(Document("_id", id), upd).awaitFirstOrNull()
        require(updated != null)
        require(updated.matchedCount == 1L && updated.modifiedCount == 1L)

        val new = coll.find(Document("_id", id)).projection(include(props.map { (name, _) -> name.name })).awaitFirstOrNull()
        if (new == null) return@suspended

        val updates = props.map { (name, _) ->
            old.getOrDefault<String, Any?>(name.name, null).let { old ->
                new.getOrDefault(name.name, null).let { new -> name to (old to new) }
            }
        }

        events.entityUpdated(type.instance<Any>(this@MongoDatastore, id), updates)
        Unit
    }

    override suspend fun <ID : Any, E : KIEntity<ID>> query(f: FilterWrapper<ID, E>): Try<Iterable<E>> = Try.suspended(GlobalScope) {
        val meta = f.meta
        log.debug { "$meta with ${meta.baseMeta}" }
        val coll = getCollection(meta)
        val pipeline = f.pipeline
        log.trace { "pipeline for $f: $pipeline" }
        coll.aggregate(pipeline).asFlow().map {
            @Suppress("UNCHECKED_CAST")
            meta.instance<ID>(this, it.get("_id") as ID) as E
        }.toList(mutableListOf())
    }

    private inline suspend fun <R> tx(options: TransactionOptions = TransactionOptions.builder().build(), work: ClientSession.() -> Pair<R, suspend () -> Unit>): R =
            mongoClient.startSession().awaitFirstOrNull()?.run {
        use {
            startTransaction(options)
            val (res, afterCommit) = work(this)
            commitTransaction()
            afterCommit()
            res
        }
    } ?: throw DatastoreException(this, "could not start session")

    companion object {
        const val METAKEY = "_meta"
    }
}


val FilterWrapper<*,*>.pipeline : List<Document>
  get() = listOf(Document("\$match", Document("\$and", listOf(
          eq("${MongoDatastore.METAKEY}.${KIEntityMeta.TYPESKEY}", meta.name),
          f.bson
  )
  )))
val Filter<*, *>.bson : Document
  get() = when(this) {
      is FilterWrapper<*,*> -> f.bson
      is LogicalFilter<*,*> -> when(this) {
          is AndFilter<*,*> -> Document("\$and", content.map { it.bson })
          is OrFilter<*, *> -> Document("\$or", content.map { it.bson })
      }
      is AllFilter<*,*> -> Document()
      is NoneFilter<*,*> -> Document("_id", Document("\$exists", false))
      is PropertyFilter<*,*,*> -> when(this) {
              is ComparisonFilter<*,*,*> -> when(this) {
                  is GTFilter<*,*,*> -> Document(prop.name, Document("\$gt", value))
                  is LTFilter<*,*,*> -> Document(prop.name, Document("\$lt", value))
              }
      }
  }