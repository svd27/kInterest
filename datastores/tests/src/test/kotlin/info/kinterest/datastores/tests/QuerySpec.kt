package info.kinterest.datastores.tests

import info.kinterest.Query
import info.kinterest.datastore.Datastore
import info.kinterest.datastores.tests.jvm.EmployeeTransient
import info.kinterest.datastores.tests.jvm.ManagerTransient
import info.kinterest.filter.filter
import info.kinterest.projection.EntityProjection
import info.kinterest.projection.ParentProjection
import info.kinterest.projection.paging.Paging
import io.kotest.core.spec.style.FreeSpec
import io.kotlintest.provided.ProjectConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.kodein.di.DI
import org.kodein.di.instance
import org.kodein.di.on

@ExperimentalCoroutinesApi
class QuerySpec : FreeSpec({
    val kodein = DI {
        extend(kodeinTest)
    }
    val spec = this


    ProjectConfig.datastores.forEach {
        which ->
        "!given a datastore $which" - {
            val ds: Datastore by kodein.on(ProjectConfig).instance<DataStoreTypeAndName,Datastore>(arg = DataStoreTypeAndName(which, "${spec::class.simpleName}${which}ds1"))
            "some entities" - {
                val employees = List(100) {
                    EmployeeTransient(salary = it, name = "name$it", first = "first$it", age = it, someLong = if(it%2==0) 1L else 11L)
                }
                ds.create(employees)
                val managers = List(100) {
                    ManagerTransient(department = "department $it", name = "name$it", first = "first$it", age = it, someLong = if(it%2==0) 1L else 11L, salary = it+1000)
                }
                "and a simple projection without sorting" - {
                    var parent = ParentProjection<Long, Person>(mapOf())
                    parent = parent + EntityProjection("entities", Paging(0, 5), null, parent)
                    val f = filter<Long,Person>(info.kinterest.datastores.tests.jvm.PersonJvm) {
                        50 lte "age"
                    }
                    val query = Query<Long,Person>(f, parent)
                    /*
                    val resTry = ds.query(query)
                    resTry.isSuccess.shouldBeTrue()
                    val res = resTry.getOrDefault { throw it }
                    res.query.shouldBe(query)
                    res.result.results.keys.shouldHaveSize(1)
                    val projectionResult = res.result.results.values.first()
                    require(projectionResult is EntityProjectionResult<*,*>)
                    projectionResult.name.shouldBe("entities")
                    projectionResult.page.entities.shouldHaveSize(5)
                     */
                }
            }
        }
    }
}) {
    init {

    }
}