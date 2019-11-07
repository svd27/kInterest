package info.kinterest.datastores.tests

import info.kinterest.datastore.Datastore
import info.kinterest.datastores.tests.jvm.EmployeeTransient
import info.kinterest.datastores.tests.jvm.ManagerTransient
import info.kinterest.datastores.tests.jvm.PersonJvm
import info.kinterest.datastores.tests.jvm.PersonTransient
import info.kinterest.filter.filter
import info.kinterest.functional.getOrDefault
import info.kinterest.functional.getOrElse
import io.kotlintest.Spec
import io.kotlintest.forAll
import io.kotlintest.matchers.asClue
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.types.shouldBeInstanceOf
import io.kotlintest.provided.ProjectConfig
import io.kotlintest.shouldBe
import io.kotlintest.specs.FreeSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.kodein.di.Kodein
import org.kodein.di.generic.M
import org.kodein.di.generic.instance
import org.kodein.di.generic.on

@ExperimentalCoroutinesApi
class QueryFilterSpec : FreeSpec({
    val log = KotlinLogging.logger { }
    val kodein = Kodein {
        extend(kodeinTest)
    }
    val spec: Spec = this

    forAll(ProjectConfig.datastores) { which ->
        "for type: $which" - {
            "querying for a single entity" - {
                val ds: Datastore by kodein.on(ProjectConfig).instance(arg = M(which, "${spec::class.simpleName}ds1"))
                ds.register(PersonJvm)
                val pt = PersonTransient(mutableMapOf<String,Any?>("name" to "djuric", "first" to "sasa", "age" to 3, "someLong" to 10L))
                val pe = ds.create(pt).fold({ throw it }) { it }.first()
                require(pe is Person)
                pe.name.shouldBe("djuric")
                val retrieved = ds.retrieve(pe._meta, pe.id).fold({ throw it }) { it }.first()
                retrieved.id.shouldBe(pe.id)
                val filter = filter<Long, Person>(PersonJvm) {
                    4 gte "age" or (10 lte "age")
                }
                pe.name = "duric"
                val queryFlow = ds.query(filter)
                queryFlow.map { flow -> flow.onCompletion { log.info { "flow done $it" } } }
                queryFlow.isSuccess.shouldBeTrue()
                val queryRes = queryFlow.getOrElse { throw it }.toList(mutableListOf())
                queryRes.asClue {
                    it.shouldHaveSize(1)
                    it.first().name.shouldBe("duric")
                }
            }
            "querying for entities in a hierarchy" - {
                val ds: Datastore by kodein.on(ProjectConfig).instance(arg = M(which, "${spec::class.simpleName}ds2"))
                ds.register(PersonJvm)
                ds.register(info.kinterest.datastores.tests.jvm.EmployeeJvm)
                ds.register(info.kinterest.datastores.tests.jvm.ManagerJvm)

                val pt = PersonTransient(mutableMapOf<String,Any?>("name" to "djuric", "first" to "sasa", "age" to 3, "someLong" to 10L))

                val ee = EmployeeTransient(mutableMapOf<String,Any?>("name" to "djuric", "first" to "sasa", "age" to 3, "someLong" to 10L, "salary" to 10000))
                val me = ManagerTransient(mutableMapOf<String,Any?>("name" to "djuric", "first" to "sasa", "age" to 3, "someLong" to 10L, "salary" to 10000, "department" to null))
                val crtRes = ds.create(pt, ee, me).fold({ throw it }) { it }.toList(mutableListOf())
                crtRes.shouldHaveSize(3)

                val filter = filter<Long, Person>(PersonJvm) {
                    4 gte "age" or (10 lte "age")
                }
                val qpers = ds.query(filter)
                qpers.isSuccess.shouldBeTrue()
                qpers.getOrElse { throw it }.toList(mutableListOf()).asClue {
                    it.shouldHaveSize(3)
                }

                val f = filter<Long, Employee>(info.kinterest.datastores.tests.jvm.EmployeeJvm) {
                    4 gte "age" and (10001 gte "salary")
                }
                val qres = ds.query(f)
                qres.getOrElse { log.debug(it) { "error on query" } }
                qres.isSuccess.shouldBeTrue()
                qres.getOrDefault { throw it }.toList(mutableListOf()).shouldHaveSize(2)

                val f1 = filter<Long, Manager>(info.kinterest.datastores.tests.jvm.ManagerJvm) {
                    4 gte "age" and (10001 gte "salary")
                }
                val qres1 = ds.query(f1)
                qres1.isSuccess.shouldBeTrue()

                qres1.getOrDefault { throw it }.toList(mutableListOf()).asClue {
                    it.shouldHaveSize(1)
                    it.first().shouldBeInstanceOf<Manager>()
                }
            }
        }
    }
})