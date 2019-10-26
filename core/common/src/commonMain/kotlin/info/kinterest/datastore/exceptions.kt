package info.kinterest.datastore

import info.kinterest.entity.KIEntityMeta

sealed class DatastoreError(val ds:Datastore, message:String?="", cause:Throwable?=null) : Exception("DataStore ${ds.name} Error: $message", cause)
class DatastoreException(ds:Datastore, message: String="", cause: Throwable?=null) : DatastoreError(ds, message, cause)
class DatastoreKeyNotFound(val meta: KIEntityMeta, id:Any, ds: Datastore, cause: Throwable?=null) : DatastoreError(ds, "Key $id not found for Entity ${meta.type}", cause)
class DatastoreUnknownType(val meta: KIEntityMeta, ds: Datastore, cause: Throwable?=null) : DatastoreError(ds, "Type ${meta.type} not known")
