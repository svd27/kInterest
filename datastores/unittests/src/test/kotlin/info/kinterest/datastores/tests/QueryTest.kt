package info.kinterest.datastores.tests

import info.kinterest.datastore.Datastore
import info.kinterest.datastores.kodeinDatastores
import info.kinterest.datastores.tests.jvm.EmployeeTransient
import info.kinterest.datastores.tests.jvm.ManagerTransient
import info.kinterest.datastores.tests.jvm.PersonTransient
import info.kinterest.filter.filter
import info.kinterest.functional.getOrDefault
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance
import org.kodein.di.generic.on
import org.kodein.di.generic.with
import strikt.api.expectThat
import strikt.assertions.*

val Any.unit get() = Unit

@DisplayName("Query")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryTest : KodeinAware {
    override lateinit var kodein: Kodein
    private val log = KotlinLogging.logger {}

    @BeforeAll
    fun beforeAll() {
        log.info { "before all" }
        TestScope.getRegistry(this)
        kodein = initKodein("testMongo", "testHazelcast")
    }

    @AfterAll
    fun afterAll() {
        log.info { "after all" }
        TestScope.close(this)
    }

    fun initKodein(mongoDbName:String, hazelcastName:String) : Kodein = Kodein {
        import(kodeinTest)
    }


    @ParameterizedTest
    @ValueSource(strings = ["mongoDb", "hazelcast"])
    fun simpleQueryTest(which:String) : Unit = runBlocking {
        val ds: Datastore by kodein.on(this).instance(which)
        ds.register(info.kinterest.datastores.tests.jvm.PersonJvm)

        val pt = PersonTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa", "age" to 3, "someLong" to 10L))
        val pe = ds.create(pt).fold({ throw it }) { assert(it.size == 1); it.first() }
        require(pe is Person)
        expectThat(pe.name).isEqualTo("djuric")
        pe.name = "duric"
        expectThat(pe.name).isEqualTo("duric")
        val retrieved = ds.retrieve(pe._meta, pe.id).fold({ throw it }) { it }.first()
        expectThat(retrieved.id) {
            isEqualTo(pe.id)
        }
        val filter = filter<Long,Person>(info.kinterest.datastores.tests.jvm.PersonJvm) {
            4 gte "age" or (10 lte "age")
        }
        val queryRes = ds.query(filter)
        expectThat(queryRes.isSuccess).isTrue()
        val res = queryRes.getOrDefault { listOf() }
        expectThat(res.toList()) {
            hasSize(1)
        }
        expectThat(res.first().name).isEqualTo("duric")
    }.unit

    @ParameterizedTest
    @ValueSource(strings = ["mongoDb", "hazelcast"])
    fun hierarchyTest(which: String) : Unit = runBlocking {
        val ds: Datastore by kodein.on(this).instance(which)
        ds.register(info.kinterest.datastores.tests.jvm.PersonJvm)
        ds.register(info.kinterest.datastores.tests.jvm.EmployeeJvm)
        ds.register(info.kinterest.datastores.tests.jvm.ManagerJvm)

        val pt = PersonTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa", "age" to 3, "someLong" to 10L))

        val ee = EmployeeTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa", "age" to 3, "someLong" to 10L, "salary" to 10000))
        val me = ManagerTransient(null, mutableMapOf("name" to "djuric", "first" to "sasa", "age" to 3, "someLong" to 10L, "salary" to 10000, "department" to null))
        val crtRes = ds.create(pt, ee, me).fold({ throw it }) { it }
        expectThat(crtRes).hasSize(3)
        val f = filter<Long, Employee>(info.kinterest.datastores.tests.jvm.EmployeeJvm) {
            4 gte "age" and (10001 gte "salary")
        }
        val qres = ds.query(f)
        if(qres.isFailure) {
            qres.fold({log.debug(it) { "error on query" }}) {Unit}
        }
        expectThat(qres.isSuccess).isTrue()
        val res = qres.getOrDefault { listOf() }.toList()
        expectThat(res) {
            hasSize(2)
        }

        val f1 = filter<Long, Manager>(info.kinterest.datastores.tests.jvm.ManagerJvm) {
            4 gte "age" and (10001 gte "salary")
        }
        val qres1 = ds.query(f1)
        if(qres1.isFailure) {
            qres.fold({log.debug(it) { "error on query" }}) {Unit}
        }
        expectThat(qres1.isSuccess).isTrue()
        val res1 = qres1.getOrDefault { listOf() }.toList()
        expectThat(res1) {
            hasSize(1)
        }
        expectThat(res1[0]).isA<Manager>()
    }.unit
}