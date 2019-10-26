package info.kinterest.datastores.mongo

import info.kinterest.annotations.Entity
import info.kinterest.entity.KIEntity
import org.bson.types.ObjectId

@Entity
interface One : KIEntity<ObjectId> {
    val name : String
}