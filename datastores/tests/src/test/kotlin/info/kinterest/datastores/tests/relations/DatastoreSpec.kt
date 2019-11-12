package info.kinterest.datastores.tests.relations

import info.kinterest.datastore.Datastore
import info.kinterest.datastores.tests.kodeinTest
import info.kinterest.datastores.tests.relations.jvm.PersonJvm
import info.kinterest.datastores.tests.relations.jvm.PersonTransient
import info.kinterest.filter.filter
import info.kinterest.functional.getOrElse
import io.kotlintest.forAll
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.matchers.types.shouldNotBeNull
import io.kotlintest.provided.ProjectConfig
import io.kotlintest.specs.FreeSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.kodein.di.Kodein
import org.kodein.di.generic.M
import org.kodein.di.generic.instance
import org.kodein.di.generic.on

@ExperimentalCoroutinesApi
class DatastoreSpec : FreeSpec({
    val log = KotlinLogging.logger { }
    val kodein = Kodein {
        extend(kodeinTest)
    }
    forAll(ProjectConfig.datastores) { which ->
        val ds: Datastore by kodein.on(ProjectConfig).instance(arg = M(which, "${which}1"))
        "given a Datastore $which" - {
            ds.register(PersonJvm)
            val pt1 = PersonTransient(44, "holla", "olla", null)
            val pt2 = PersonTransient(47, "rolla", "olla", null)
            val res = ds.create<Long, PersonTransient, Person>(listOf(pt1, pt2)).getOrElse { throwable -> throw throwable }.toList(mutableListOf())
            res.shouldHaveSize(2)
            res.map { it.name }.toSet().shouldContainExactly("olla")
            res.first { it.age == 44 }.marry(res.first { it.age == 47 })
            val f = filter<Long, Person>(PersonJvm) {
                45 gte "age"
            }
            val qr = ds.query(f).getOrElse { throw it }.toList(mutableListOf())
            log.debug { qr }
            qr.shouldHaveSize(1)
            qr.first().spouse.shouldNotBeNull()
            log.debug { qr }
        }
        "just mucking about" - {
            Populate.populate(ds)
            1.shouldNotBeNull()
        }
    }
})