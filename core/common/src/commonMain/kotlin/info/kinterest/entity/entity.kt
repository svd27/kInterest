package info.kinterest.entity

import info.kinterest.DONTDOTHIS
import info.kinterest.datastore.Datastore

interface KIEntity<out ID:Any> {
    val id : ID
    val _store : Datastore
    val _meta : KIEntityMeta

    fun<V> getValue(property: PropertyMeta) : V?
    fun<V> setValue(property: PropertyMeta, v:V?)
    fun<ID:Any, E:KIEntity<ID>> getRelations(property: RelationProperty) : Collection<E>
    fun asTransient() : KITransientEntity<ID>

    fun _equals(o:Any?) : Boolean = o is KIEntity<*> && o._meta == _meta && o.id==id
    fun _hashCode() : Int = id.hashCode()
    fun _toString() : String = """${_meta.name}($id)"""
}


interface KIVersioned<V:Any> {
    val _version : V
}

interface KITransientEntity<out ID:Any> : KIEntity<ID> {
    @Suppress("UNCHECKED_CAST")
    override val id : ID get() = properties["id"] as? ID ?: DONTDOTHIS()
    val hasID : Boolean get() = properties["id"] != null

    val properties : MutableMap<String,Any?>

    @Suppress("UNCHECKED_CAST")
    override fun<V> getValue(property: PropertyMeta) : V? = properties[property.name] as? V?
    override fun<V> setValue(property: PropertyMeta, v:V?) {
        properties[property.name] = v
    }

    override fun <ID : Any, E : KIEntity<ID>> getRelations(property: RelationProperty): Collection<E> = emptyList()

    override fun _equals(o:Any?) : Boolean = o is KIEntity<*> && o._meta == _meta && o.id==id
    override fun _toString() : String = """${this::class.simpleName}($properties)"""
}

var<ID:Any> KITransientEntity<ID>._id : ID?
    get() = if(hasID) id else null
    set(value) { properties["id"] = value }