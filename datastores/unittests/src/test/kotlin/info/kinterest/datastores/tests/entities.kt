package info.kinterest.datastores.tests

import info.kinterest.annotations.Entity
import info.kinterest.entity.KIEntity
import org.bson.types.ObjectId

@Entity
interface Person : KIEntity<Long> {
    var name : String
    var first : String
    var age : Int?
    val someLong : Long
}

@Entity
interface Employee : Person {
    var salary : Int
}

@Entity
interface Manager : Employee {
    var department : String?
}