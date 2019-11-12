package info.kinterest.datastores.tests.relations

import info.kinterest.annotations.Entity
import info.kinterest.entity.KIEntity
import info.kinterest.entity.SingleRelationProperty
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Entity
interface Person : KIEntity<Long> {
    val name : String
    val first : String
    var age : Int
    val spouse : Person?

    fun marry(spouse:Person) {
        GlobalScope.launch {
            _store.setRelations(_meta, id, SingleRelationProperty("spouse", _meta, true, true), listOf(spouse))
        }
    }
}

@Entity
interface Employee : Person {
    val salary : Int
    val department : Department
}

@Entity
interface Manager : Employee {
    val manages : Department

}


@Entity
interface Department : KIEntity<Long> {
    val name : String
    val parent : Department?
}