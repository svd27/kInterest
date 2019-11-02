package info.kinterest.datastore

import info.kinterest.entity.KIEntity
import info.kinterest.entity.KIEntityMeta
import info.kinterest.functional.Try

interface DatastoreManager {
    suspend fun retrieve(store:Datastore, type: KIEntityMeta, ids:Iterable<Any>) : Try<Collection<KIEntity<Any>>>
    suspend fun retrieve(stores:Set<Datastore>, type: KIEntityMeta, ids:Iterable<Any>) : Try<Collection<KIEntity<Any>>>
}