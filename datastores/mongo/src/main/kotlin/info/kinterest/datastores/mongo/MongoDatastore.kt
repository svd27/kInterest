package info.kinterest.datastores.mongo

import com.mongodb.TransactionOptions
import com.mongodb.WriteConcern
import com.mongodb.client.model.Filters.*
import com.mongodb.client.model.Projections.include
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.Updates
import com.mongodb.reactivestreams.client.ClientSession
import com.mongodb.reactivestreams.client.MongoClients
import com.mongodb.reactivestreams.client.MongoCollection
import com.mongodb.reactivestreams.client.MongoDatabase
import info.kinterest.DONTDOTHIS
import info.kinterest.Query
import info.kinterest.QueryResult
import info.kinterest.datastore.DatastoreException
import info.kinterest.datastore.DatastoreUnknownType
import info.kinterest.datastore.EventManager
import info.kinterest.datastore.IdGenerator
import info.kinterest.datastores.AbstractDatastore
import info.kinterest.entity.*
import info.kinterest.filter.*
import info.kinterest.functional.Try
import info.kinterest.functional.getOrElse
import info.kinterest.functional.suspended
import info.kinterest.projection.ParentProjectionResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.bson.Document
import org.bson.types.ObjectId
import java.util.*


@ExperimentalCoroutinesApi
class MongoDatastore(cfg: MongodatastoreConfig, events: EventManager) : AbstractDatastore(cfg, events) {
    val log = KotlinLogging.logger { }
    override val name: String = cfg.name
    internal val mongoClient = MongoClients.create(cfg.asConnectionString().apply { log.debug { "client on $this" } })
    internal val db: MongoDatabase
    internal val countersDb: MongoDatabase

    inner class LongGenerator(val name: String) : IdGenerator<Long> {
        val collection: MongoCollection<Document> = db.getCollection("counters").withWriteConcern(WriteConcern.MAJORITY)

        init {
            runBlocking {
                log.debug {
                    runBlocking {
                        "!!!BEFORE documents in counters with concern ${collection
                                .writeConcern}: ${collection.countDocuments().awaitLast()}\n${collection.find().asFlow().toList(mutableListOf())}"
                    }
                }
                mongoClient.startSession().awaitFirstOrNull()?.use { session ->
                    session.startTransaction()
                    val counter: Document? = collection.find(eq("_id", name)).awaitFirstOrNull()
                    if (counter == null) {
                        log.info { "insert initial counter for $name" }
                        collection.insertOne(Document("_id", name).append("counter", 0L)).awaitLast()
                    }
                    session.commitTransaction()
                }

                log.debug {
                    runBlocking {
                        "!!!AFTER documents in counters with concern ${collection
                                .writeConcern}: ${collection.countDocuments().awaitLast()}\n${collection.find().asFlow().toList(mutableListOf())}"
                    }
                }
            }
        }

        override fun next(): Long = runBlocking {
            val counter = collection.findOneAndUpdate(Document("_id", name), Document("\$inc", mutableMapOf("counter" to 1L))).awaitLast()["counter"]
            require(counter is Long)
            log.debug { "returning $counter for $name" }
            counter as? Long
                    ?: throw DatastoreException(this@MongoDatastore, "bad type of counter expected Long but found ${counter::class}")
        }
    }

    class ObjectIdGenerator() : IdGenerator<ObjectId> {
        override fun next(): ObjectId = ObjectId()
    }

    override fun getIdGenerator(meta: KIEntityMeta): IdGenerator<*> = run {
        val idType = meta.baseMeta.idType
        when (idType) {
            is LongPropertyMeta -> LongGenerator(meta.baseMeta.name)
            is ReferenceProperty -> when (idType.type) {
                ObjectId::class -> ObjectIdGenerator()
                UUID::class -> UUIDGenerator
                else -> throw DatastoreException(this, "unsupported type for autogenerate $idType")
            }
            else -> throw DatastoreException(this, "unsupported type for autogenerate $idType")
        }
    }

    val dbLock: Mutex = Mutex()
    val collections: MutableMap<KIEntityMeta, MongoCollection<Document>> = mutableMapOf()
    private val metas: MutableMap<String, KIEntityMeta> = mutableMapOf()

    init {
        //(log.underlyingLogger as ch.qos.logback.classic.Logger).level = Level.TRACE
        countersDb = runBlocking {
            val db = mongoClient.getDatabase("_counters_")
            if (db.getCollection("counters") == null) {
                db.createCollection("counters").awaitLast()
            }
            db
        }
        db = mongoClient.getDatabase(name)
        GlobalScope.launch {
            ready()
        }
    }

    override suspend fun register(meta: KIEntityMeta) {
        val name = meta.baseMeta.name
        dbLock.withLock {
            mongoClient.startSession().awaitLast().use {
                it.startTransaction()
                if (db.getCollection(name) == null) {
                    db.createCollection(name).awaitLast()
                }
                it.commitTransaction()
                collections[meta] = db.getCollection(name)
            }
            metas[meta.name] = meta
            if (meta.idGenerated) {
                val idType = meta.idType
                if (idType !is ReferenceProperty || idType.type != ObjectId::class) {
                    when (idType) {
                        is LongPropertyMeta -> idGenerators.getOrPut(meta.baseMeta, { LongGenerator(meta.baseMeta.name) })
                        else -> throw DatastoreException(this, "unsupported type for autogenerate $idType")
                    }
                }
            }
        }
    }

    fun getCollection(meta: KIEntityMeta): MongoCollection<Document> = collections.getOrElse(meta.baseMeta) {
        throw DatastoreUnknownType(meta, this)
    }

    fun getMeta(name: String): KIEntityMeta = metas.getOrElse(name) { throw DatastoreException(this, "unknown meta $name") }

    override fun <ID : Any, E : KIEntity<ID>> retrieve(type: KIEntityMeta, vararg ids: ID): Try<Flow<E>> = retrieve(type, ids.asList())

    override fun <ID : Any, E : KIEntity<ID>> retrieve(type: KIEntityMeta, ids: Iterable<ID>): Try<Flow<E>> = Try {
        log.trace { "retrieve $type $ids" }
        val collection = getCollection(type)
        collection.find(`in`("_id", ids)).asFlow().map {
            log.trace { "retrieve $it" }
            @Suppress("UNCHECKED_CAST")
            type.instance<Any>(this, it["_id"]!!) as E
        }
    }


    @FlowPreview
    override fun <ID : Any, E : KITransientEntity<ID>, R : KIEntity<ID>> create(vararg entities: E): Try<Flow<R>> = create(entities.toList())

    @FlowPreview
    override fun <ID : Any, E : KITransientEntity<ID>, R : KIEntity<ID>> create(entities: Iterable<E>): Try<Flow<R>> = Try {
        val es = entities.toList()
        val collection = if (es.isNotEmpty()) {
            val _meta = es[0]._meta
            getCollection(_meta)
        } else throw DatastoreException(this, "no entities supplied")
        val _meta = es[0]._meta.baseMeta

        val includeId = if (_meta.idGenerated) {
            if (_meta.idType !is ReferenceProperty || _meta.idType.type != ObjectId::class) {
                val gen = idGenerators.getOrElse(_meta) { throw DatastoreException(this, "no generator found for $_meta") }
                es.forEach {
                    @Suppress("UNCHECKED_CAST")
                    it._id = gen.next() as ID
                }
                true
            } else false
        } else true
        val docs: List<Document> = es.fold(listOf()) { acc, e ->
            acc + e.properties.entries.filter { it.key != "id" && e._meta.properties[it.key] !is RelationProperty }.fold(Document()) { doc, p ->
                doc.append(p.key, p.value)
            }.also {
                if (includeId) it.append("_id", e._id)

                val relations: Map<RelationProperty, List<RelationTo>> = e.properties.entries.filter { e._meta.properties[it.key] is RelationProperty }.map {
                    val property: RelationProperty = e._meta.properties[it.key] as? RelationProperty ?: DONTDOTHIS()
                    val list = when (property) {
                        is SingleRelationProperty -> e.getValue<KIEntity<Any>>(property)?.run { listOf(RelationTo(e._meta, property, _meta, id, _store.name)) }
                        is CollectionRelationProperty -> e.getValue<Collection<KIEntity<Any>>>(property)?.map { RelationTo(e._meta, property, it._meta, it.id, it._store.name) }
                    }

                    if (list != null) property to list else null
                }.filterNotNull().toMap()
                val metaInfo = e._meta.metaBlock.copy(outgoing = relations)
                it.append(METAKEY, metaInfo.asMap())
                log.trace { "document $it" }
            }
        }

        log.trace { "docs: $docs" }
        collection.insertMany(docs).asFlow().flatMapConcat {
            docs.map { it.get("_id") }.filterNotNull().map {
                @Suppress("UNCHECKED_CAST")
                _meta.instance<ID>(this, it) as R
            }.apply {
                events.entitiesCreated(this)
            }.asFlow()
        }
    }

    override suspend fun <ID : Any, E : KIEntity<ID>> delete(vararg entities: E): Try<Set<ID>> = delete(entities.asIterable())

    override suspend fun <ID : Any, E : KIEntity<ID>> delete(entities: Iterable<E>): Try<Set<ID>> = Try.suspended {
        val es = entities.toList()
        if (es.isEmpty()) return@suspended setOf<ID>()
        val type = es[0]._meta
        val collection = getCollection(type)

        val ids: Set<ID> = es.map { it.id }.toSet()
        val result = collection.deleteMany(`in`("_id", ids)).awaitLast()
        if (result.deletedCount == es.size.toLong()) ids else {
            val retrieved = retrieve(type, ids).getOrElse { throw it }
            retrieved.fold(ids) { acc, e ->
                acc - e.id
            }
        }.apply { events.entitiesDeleted(type, this) }
    }

    override suspend fun getValues(type: KIEntityMeta, id: Any, props: Set<PropertyMeta>): Try<Collection<Pair<PropertyMeta, Any?>>> = Try.suspended(GlobalScope) {
        val coll = getCollection(type)
        val doc = coll.find(Document("_id", id)).projection(include(props.map { it.name })).awaitLast()
        props.map { name -> name to doc[name.name] }
    }

    override suspend fun setValues(type: KIEntityMeta, id: Any, props: Map<PropertyMeta, Any?>): Try<Unit> = Try.suspended(GlobalScope) {
        val coll = getCollection(type)
        if (props.isEmpty()) return@suspended

        val old = coll.find(Document("_id", id)).projection(include(props.map { (name, _) -> name.name })).awaitFirstOrNull()
                ?: return@suspended
        val effective = props.filter { (name, value) -> old[name.name] != value }.map { (key, value) -> key.name to value }

        val upd = Document("\$set", effective.toMap())
        val updated = coll.updateOne(Document("_id", id), upd).awaitFirstOrNull()
        require(updated != null)
        require(updated.matchedCount == 1L && updated.modifiedCount == 1L)

        val new = coll.find(Document("_id", id)).projection(include(props.map { (name, _) -> name.name })).awaitFirstOrNull()
                ?: return@suspended

        val updates = props.map { (name, _) ->
            old.getOrDefault<String, Any?>(name.name, null).let { old ->
                new.getOrDefault(name.name, null).let { new -> name to (old to new) }
            }
        }

        events.entityUpdated(type.instance<Any>(this@MongoDatastore, id), updates)
        Unit
    }

    override fun <ID : Any, E : KIEntity<ID>> query(f: FilterWrapper<ID, E>): Try<Flow<E>> = Try {
        val meta = f.meta
        log.debug { "$meta with ${meta.baseMeta}" }
        val coll = getCollection(meta)
        val pipeline = f.pipeline
        log.trace { "pipeline for $f: $pipeline" }
        coll.aggregate(pipeline).asFlow().map {
            @Suppress("UNCHECKED_CAST")
            meta.instance<ID>(this, it.get("_id") as ID) as E
        }
    }

    suspend fun <ID : Any, E : KIEntity<ID>> query(query: Query<ID, E>): Try<QueryResult<ID, E>> = Try.suspended {
        val collection = getCollection(query.f.meta)
        collection.toString()
        if (query.projection.projections.isEmpty()) {
            QueryResult(query, ParentProjectionResult(query.projection, mapOf()))
        } else {
            QueryResult(query, ParentProjectionResult(query.projection, mapOf()))
        }
    }

    private suspend inline fun <R> tx(options: TransactionOptions = TransactionOptions.builder().build(), work: ClientSession.() -> Pair<R, suspend () -> Unit>): R =
            mongoClient.startSession().awaitFirstOrNull()?.run {
                use {
                    startTransaction(options)
                    val (res, afterCommit) = work(this)
                    commitTransaction()
                    afterCommit()
                    res
                }
            } ?: throw DatastoreException(this, "could not start session")

    override suspend fun <ID : Any, E : KIEntity<ID>> addRelations(type: KIEntityMeta, id: Any, prop: RelationProperty, entities: Collection<E>) {
        val collection = getCollection(type)

        val froms = entities.map { RelationFrom(prop, type, id, name) }
        addIncomingRelations(id, froms)
        val upd = Updates.addEachToSet(OUTGOINGPATH, entities.fold(listOf<Map<String, Any>>()) { acc, e ->
            acc + RelationTo(type, prop, e._meta, e.id, e._store.name).toMap()
        })


        collection.findOneAndUpdate(eq("_id", id), upd).awaitLast()
    }



    override suspend fun <ID : Any, E : KIEntity<ID>> setRelations(type: KIEntityMeta, id: Any, prop: RelationProperty, entities: Collection<E>) {
        val collection = getCollection(type)

        val upd = Updates.set("$OUTGOINGPATH.${prop.name}", entities.fold(listOf<Map<String, Any>>()) { acc, e ->
            acc + RelationTo(type, prop, e._meta, e.id, e._store.name).toMap()
        })
        collection.findOneAndUpdate(eq("_id", id), upd).awaitLast()
    }

    override suspend fun <ID : Any, E : KIEntity<ID>> removeRelations(type: KIEntityMeta, id: Any, prop: RelationProperty, entities: Collection<E>) {
        val collection = getCollection(type)

        val upd = Updates.pullAll("$OUTGOINGPATH.${prop.name}", entities.fold(listOf<Map<String, Any>>()) { acc, e ->
            acc + RelationTo(type, prop, e._meta, e.id, e._store.name).toMap()
        })
        collection.findOneAndUpdate(eq("_id", id), upd).awaitLast()
    }

    @FlowPreview
    override fun <ID : Any, E : KIEntity<ID>> getRelations(type: KIEntityMeta, id: Any, prop: RelationProperty): Try<Flow<E>> = Try {
        val collection = getCollection(type)

        collection.find(and(eq("_id", id))).projection(include("$OUTGOINGPATH.${prop.name}")).asFlow().flatMapConcat { doc ->
            Try {
                val l = doc.getList("$OUTGOINGPATH.${prop.name}", List::class.java)
                log.debug { l }
            }.getOrElse { log.debug { "nope!!!" } }
            val relDocs = doc.get(METAKEY, Document::class.java)
                    .get(KIEntityMeta.RELATIONSKEY, Document::class.java)
                    .get(KIEntityMeta.OUTGOING, Document::class.java)
                    .get(prop.name, List::class.java).filterIsInstance<Document>()
            val res = relDocs.map { RelationTo.fromMap(type, it, ::getMeta) }
            log.debug { "relations $res" }
            if(res.isEmpty()) listOf<E>().asFlow() else
            retrieve<ID,E>(prop.contained, res.map {
                @Suppress("UNCHECKED_CAST")
                it.toId as ID
            }).getOrElse { throw it }
        }
    }

    override suspend fun addIncomingRelations(id: Any, relations: Collection<RelationFrom>)  {
        if(relations.isEmpty()) return

        val collection = getCollection(relations.first().relation.contained)


        val modified = collection.bulkWrite(relations.map {
            UpdateOneModel<Document>(eq("_id", id), Updates.addToSet("$INCOMINGPATH.${it.fromType.name}.${it.relation.name}", it.toMap()))
        }).awaitLast().modifiedCount

        if(modified!=relations.size) log.warn { "inconsistent count of updates. was: $modified expected: ${relations.size}" }

        Unit
    }

    override suspend fun setIncomingRelations(id: Any, relations: Collection<RelationFrom>) {
        if(relations.isEmpty()) return

        val collection = getCollection(relations.first().relation.contained)


        val modified = collection.bulkWrite(relations.map {
            UpdateOneModel<Document>(eq("_id", id), Updates.set("$INCOMINGPATH.${it.fromType.name}.${it.relation.name}", it.toMap()))
        }).awaitLast().modifiedCount

        if(modified!=relations.size) log.warn { "inconsistent count of updates. was: $modified expected: ${relations.size}" }
    }

    override suspend fun removeIncomingRelations(id: Any, relations: Collection<RelationFrom>) {
        if(relations.isEmpty()) return

        val collection = getCollection(relations.first().relation.contained)


        val modified = collection.bulkWrite(relations.map {
            UpdateOneModel<Document>(eq("_id", id), Updates.pull("$INCOMINGPATH.${it.fromType.name}.${it.relation.name}", it.toMap()))
        }).awaitLast().modifiedCount

        if(modified!=relations.size) log.warn { "inconsistent count of updates. was: $modified expected: ${relations.size}" }
    }

    companion object {
        const val METAKEY = "_meta"
        const val OUTGOINGPATH = "$METAKEY.${KIEntityMeta.RELATIONSKEY}.${KIEntityMeta.OUTGOING}"
        const val INCOMINGPATH = "$METAKEY.${KIEntityMeta.RELATIONSKEY}.${KIEntityMeta.INCOMING}"
    }
}


@ExperimentalCoroutinesApi
val FilterWrapper<*, *>.pipeline: List<Document>
    get() = listOf(Document("\$match", Document("\$and", listOf(
            eq("${MongoDatastore.METAKEY}.${KIEntityMeta.TYPESKEY}", meta.name),
            f.bson
    )
    )))
val Filter<*, *>.bson: Document
    get() = when (this) {
        is FilterWrapper<*, *> -> f.bson
        is LogicalFilter<*, *> -> when (this) {
            is AndFilter<*, *> -> Document("\$and", content.map { it.bson })
            is OrFilter<*, *> -> Document("\$or", content.map { it.bson })
        }
        is AllFilter<*, *> -> Document()
        is NoneFilter<*, *> -> Document("_id", Document("\$exists", false))
        is PropertyFilter<*, *, *> -> when (this) {
            is ComparisonFilter<*, *, *> -> when (this) {
                is GTFilter<*, *, *> -> Document(prop.name, Document("\$gt", value))
                is LTFilter<*, *, *> -> Document(prop.name, Document("\$lt", value))
            }
            is RelationFilter<*,*,*,*> -> DONTDOTHIS()
        }
    }