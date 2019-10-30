package info.kinterest.datastores.mongo

import com.mongodb.reactivestreams.client.Success
import info.kinterest.DatastoreEvent
import info.kinterest.EntitiesEvent
import info.kinterest.datastore.EventManager
import info.kinterest.datastores.mongo.jvm.OneTransient
import info.kinterest.docker.client.DockerClientConfigProvider
import info.kinterest.docker.mongo.MongoCluster
import info.kinterest.entity.KIEntityMeta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactive.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import mu.KotlinLogging
import org.bson.Document
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@ExperimentalCoroutinesApi
class MongoEmbedTest: Spek({
    val cluster = MongoCluster(DockerClientConfigProvider.client())
    val log = KotlinLogging.logger {}
    cluster.start()

    describe("connecting to an embedded mongo") {
        val mongodatastoreCfg = MongodatastoreConfig("test", cluster.ip, cluster.port!!)
        val ds = MongoDatastore(mongodatastoreCfg, object : EventManager {
            override val datastore: BroadcastChannel<DatastoreEvent> = BroadcastChannel(10)
            override var entityChannels: Map<KIEntityMeta, BroadcastChannel<EntitiesEvent>> = mapOf()
            override val mutex: Mutex = Mutex()
        })
        val client = ds.mongoClient
        val db = client.getDatabase("test")
        it("works") {
            assert(db != null)
        }
        it("can insert a document") {
            val succ: Success? = runBlocking { db.createCollection("test").awaitFirstOrNull() }
            assert(succ!=null)
            val docs = db.getCollection("docs")
            val doc = Document("name", "me").append("age", 51).append("n", 0)
            assert(runBlocking { docs.insertOne(doc).awaitFirstOrNull() } != null)
            println(doc.getObjectId("_id"))
            assert(doc.getObjectId("_id") != null)
            val count = runBlocking {docs.countDocuments().awaitFirstOrNull()}
            assert(count!=null && count == 1L)
            runBlocking { docs.find().limit(100).collect { println(it); assert(it["name"] == "me") } }
        }

        it("can insert multiple documents") {
            val docs = db.getCollection("docs1")
            val documents = List(50) {
                Document("name", "me").append("age", 51).append("n", it+1)
            }

            runBlocking {  docs.insertMany(documents).awaitLast() }
            val count = runBlocking {docs.countDocuments().awaitFirstOrNull()}
            assert(count == 50L)
            runBlocking { docs.find().limit(100).collect { println(it); require(it["name"] == "me") } }
        }

        it("can insert an entity") {
            runBlocking { ds.register(info.kinterest.datastores.mongo.jvm.OneJvm) }
            val oneTransient = OneTransient(mutableMapOf<String,Any?>("name" to "sasa"))
            val res = runBlocking { ds.create(oneTransient) }
            assert(res.isSuccess)
            val entities = res.fold({throw it}) {it}
            assert(entities.size==1)
            val e = entities.first()
            log.info { e.id }
            require(e is One)
            assert(e.name == "sasa")
        }
    }
})

