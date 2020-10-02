package info.kinterest.datastores.tests.relations

import info.kinterest.datastore.Datastore
import info.kinterest.datastores.tests.DataStoreTypeAndName
import info.kinterest.datastores.tests.kodeinTest
import info.kinterest.datastores.tests.relations.jvm.PersonJvm
import info.kinterest.datastores.tests.relations.jvm.PersonTransient
import info.kinterest.filter.filter
import info.kinterest.functional.getOrElse
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotlintest.provided.ProjectConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import mu.KotlinLogging
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.di.on

@ExperimentalCoroutinesApi
class DatastoreSpec : FreeSpec({
    val log = KotlinLogging.logger { }
    val kodein = DI {
        extend(kodeinTest)
    }
    ProjectConfig.datastores.forEach { which ->
        val ds: Datastore by kodein.on(ProjectConfig).instance<DataStoreTypeAndName,Datastore>(arg = DataStoreTypeAndName(which, "${which}1"))
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
        "just mucking about $which" - {
            Populate.populate(ds)
            1.shouldNotBeNull()
        }
    }
})