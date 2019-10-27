package info.kinterest.datastores.mongo

import info.kinterest.datastore.DatastoreConfig

class MongodatastoreConfig(name:String, config: Map<String,Any?>) : DatastoreConfig(TYPE, name, config) {
    constructor(name: String, ip:String, port:Int) : this(name, mapOf("ip" to ip, "port" to port))

    val ip by config
    val port by config

    fun asConnectionString() : String = "mongodb://$ip:$port"

    companion object {
        const val TYPE : String = "mongo"
    }
}