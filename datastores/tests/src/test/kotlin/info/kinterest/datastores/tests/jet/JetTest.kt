package info.kinterest.datastores.tests.jet

import com.hazelcast.client.config.ClientNetworkConfig
import com.hazelcast.config.GroupConfig
import com.hazelcast.core.HazelcastJsonValue
import com.hazelcast.internal.json.Json
import com.hazelcast.internal.json.JsonValue
import com.hazelcast.jet.Jet
import com.hazelcast.jet.aggregate.AggregateOperations
import com.hazelcast.jet.aggregate.AggregateOperations.counting
import com.hazelcast.jet.config.JetClientConfig
import com.hazelcast.jet.config.JobConfig
import com.hazelcast.jet.datamodel.Tuple3
import com.hazelcast.jet.function.FunctionEx
import com.hazelcast.jet.function.PredicateEx
import com.hazelcast.jet.pipeline.Pipeline
import com.hazelcast.jet.pipeline.Sinks
import com.hazelcast.jet.pipeline.Sources
import com.hazelcast.query.Predicates
import info.kinterest.datastores.hazelcast.HazelcastDatastore
import info.kinterest.datastores.hazelcast.jet.FieldExtractor
import info.kinterest.datastores.hazelcast.jet.GenericComparator
import info.kinterest.datastores.hazelcast.jet.PageAggregation
import info.kinterest.datastores.hazelcast.jet.createPager
import info.kinterest.datastores.tests.relations.jvm.PersonJvm
import info.kinterest.entity.KIEntityMeta
import io.kotlintest.specs.FreeSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mu.KotlinLogging
import java.io.File

@ExperimentalCoroutinesApi
@Suppress("ObjectLiteralToLambda")
class JetTest : FreeSpec({
    val log = KotlinLogging.logger { }
    "!just mucking about" - {
        val jetCfg: JetClientConfig = JetClientConfig().apply {
            setNetworkConfig(ClientNetworkConfig().setAddresses(listOf("localhost:35992"))).setGroupConfig(GroupConfig("jet"))
        }
        val jet = Jet.newJetClient(jetCfg)
        val fatJar = File(System.getProperty("info.kinterest.datastores.hazelcast.jet.jarlocation"))

        jet.hazelcastInstance.distributedObjects.forEach {
            log.info { "${it.serviceName} ${it.name}" }
        }
        val persons = jet.hazelcastInstance.getMap<Any, HazelcastJsonValue>(PersonJvm.name)
        log.info { "persons size = ${persons.size}" }

        val filter = object : PredicateEx<Tuple3<Any, String, String>> {
            override fun testEx(t: Tuple3<Any, String, String>): Boolean {
                val key = t.f0()
                check(key is Long)
                return key in 21..39
            }
        }

        val transform = object : FunctionEx<MutableMap.MutableEntry<Any, HazelcastJsonValue>, Tuple3<Any, String, String>> {
            override fun applyEx(t: MutableMap.MutableEntry<Any, HazelcastJsonValue>?): Tuple3<Any, String, String> =
                    Json.parse(t?.value.toString()).asObject().run {
                        Tuple3.tuple3(t?.key, getString("name", ""), get(HazelcastDatastore.METAINFO).asObject().getString(KIEntityMeta.TYPEKEY, ""))
                    }
        }
        val grouping1 = object : FunctionEx<Tuple3<Any, String, String>, String> {
            override fun applyEx(t: Tuple3<Any, String, String>): String = t.f2().toString()
        }
        val grouping2 = object : FunctionEx<Tuple3<Any, String, String>, String> {
            override fun applyEx(t: Tuple3<Any, String, String>): String = t.f1()[0].toString()
        }

        val pipe = Pipeline.create()
        val ids = jet.hazelcastInstance.getFlakeIdGenerator("test")
        val res = jet.hazelcastInstance.getMap<Any, Map<String, Long>>("test-${ids.newId()}")
        val groupingStage1 = pipe.drawFrom(Sources.map(persons)).map(transform).filter(filter).groupingKey(grouping1)
        groupingStage1.aggregate(AggregateOperations.groupingBy(grouping2, counting())).drainTo(Sinks.map(res))
        jet.newJobIfAbsent(pipe, JobConfig().addClass(JetTest::class.java).addClass(transform.javaClass).addClass(grouping1.javaClass)
                .addClass(grouping2.javaClass).addClass(filter.javaClass)).join()

        log.info { res.size }
        res.forEach { log.info { it } }
        res.destroy()

        val res1 = jet.hazelcastInstance.getList<List<Tuple3<Any, String, Map<String, JsonValue>>>>("test-${ids.newId()}")

        Sources.map(persons, Predicates.alwaysTrue(), FieldExtractor(setOf("age", "name")))
        val paged = Pipeline.create()
        paged.drawFrom(Sources.map(persons, Predicates.alwaysTrue(), FieldExtractor(setOf("age", "name")))).aggregate(AggregateOperations.sorting(GenericComparator(setOf("age" to Int::class.qualifiedName!!)))).drainTo(Sinks.list(res1))
        jet.newJobIfAbsent(paged, JobConfig().addClass(FieldExtractor::class.java)
                .addJar(fatJar)).join()

        log.info { res1.size }
        res1.forEach {
            log.info { it }
        }
        log.info { res1 }

        val res2 = jet.hazelcastInstance.getList<PageAggregation>("page")
        val pagePipeline = Pipeline.create()
        val stage1 = pagePipeline.drawFrom(Sources.map(persons, Predicates.alwaysTrue(), FieldExtractor(setOf("name", "first", "age"))))

        val sort = GenericComparator(setOf("name" to String::class.qualifiedName!!, "age" to Int::class.qualifiedName!!))
        stage1.aggregate(createPager(90, 10, sort)).drainTo(Sinks.list(res2))
        jet.newJobIfAbsent(pagePipeline, JobConfig()).join()

        log.info { }
        res2.forEach { log.info { "${it.page.size} page: ${it.page} dropped: ${it.dropped}" } }
        res2.forEach { log.info { "off: ${it.offset} sz: ${it.size} fin: ${it.finished} dropped: ${it.dropped} ${it.page.map { it.f0() }}" } }
    }
})





