package info.kinterest

import info.kinterest.datastore.Datastore
import info.kinterest.entity.KIEntity
import info.kinterest.entity.KIEntityMeta
import info.kinterest.entity.PropertyName


sealed class DatastoreEvent(val ds:Datastore)
class DatastoreStarted(ds: Datastore) : DatastoreEvent(ds)

sealed class EntitiesEvent(val meta: KIEntityMeta) {
    override fun toString(): String = "EntitiesEvent(${this::class.simpleName}, $meta)"
}
class EntitiesCreated<ID:Any,E:KIEntity<ID>>(meta: KIEntityMeta, val entities:Collection<E>) : EntitiesEvent(meta) {
    override fun toString(): String = "${super.toString()} created ${entities}"
}
class EntitiesDeleted<ID:Any>(meta: KIEntityMeta, val entities:Set<ID>) : EntitiesEvent(meta) {
    override fun toString(): String = "${super.toString()} deleted ${entities}"
}
sealed class EntityEvent<ID:Any, E:KIEntity<ID>>(meta: KIEntityMeta, val entity:E) : EntitiesEvent(meta)
data class UpdateEvent<T>(val property: PropertyName, val old:T, val new:T) {
    override fun toString(): String = "$property: old = $old new = $new"
}
class EntityUpdated<ID:Any, E:KIEntity<ID>>(meta: KIEntityMeta, entity: E, val updates:List<UpdateEvent<*>>) : EntityEvent<ID,E>(meta, entity) {
    override fun toString(): String = "${super.toString()} updated $entity\n$updates"
}