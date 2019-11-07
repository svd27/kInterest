package info.kinterest.datastores.tests.relations

import info.kinterest.annotations.Entity
import info.kinterest.entity.KIEntity

@Entity
interface Person : KIEntity<Long> {
    val name : String
    val first : String
    var age : Int
    var spouse : Person?
}