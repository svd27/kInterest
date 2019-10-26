package info.kinterest.entity

import info.kinterest.datastore.Datastore
import kotlin.reflect.KClass

interface KIEntity<ID:Any> {
    val id : ID
    val _store : Datastore
    val _meta : KIEntityMeta

    fun<V> getValue(propertyName: PropertyName) : V
    fun asTransient() : KITransientEntity<ID>

    fun _equals(o:Any?) : Boolean = o is KIEntity<*> && o._meta == _meta && o.id==id
    fun _hashCode() : Int = id.hashCode()
    fun _toString() : String = """${_meta.name}($id)"""
}

inline class PropertyName(val name:String)

interface KIEntityMeta {
    val name : String
    val type : KClass<*>
    val idType : KClass<*>
    val idGenerated : Boolean
    val parentMeta : KIEntityMeta?
    val baseMeta : KIEntityMeta
    val properties : Map<PropertyName,PropertyMeta>

    fun<ID:Any> instance(_store:Datastore, id:Any) : KIEntity<ID>

    fun hierarchy() : List<KIEntityMeta> = (parentMeta?.let { it.hierarchy() }?: listOf()) + this
    fun initMetaBlock() : MetaInfo = MetaInfo(this, hierarchy(), mapOf())

    fun _equals(other:Any?) : Boolean = other is KIEntityMeta && other.type == type
    fun _hashCode() : Int = type.hashCode()
    fun _toString() : String = "Meta($name, $type)"

    companion object {
        const val TYPEKEY = "type"
        const val TYPESKEY = "types"
        const val RELATIONSKEY = "relations"
    }
}

data class MetaInfo(val type: KIEntityMeta, val types:List<KIEntityMeta>, val relations:Map<String,Any> ) {
    fun asMap() : Map<String,Any> = mapOf(KIEntityMeta.TYPEKEY to type.name, KIEntityMeta.TYPESKEY to types.map { it.name }, KIEntityMeta.RELATIONSKEY to relations)
}

interface PropertyMeta {
    val type : KClass<*>
    val nullable : Boolean
    val readOnly : Boolean
}

interface KIVersioned<V:Any> {
    val _version : V
}

interface KITransientEntity<ID:Any> {
    var _id : ID?
    val _meta : KIEntityMeta

    val properties : Map<String,Any?>
}