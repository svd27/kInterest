package info.kinterest.entity

import info.kinterest.DONTDOTHIS
import info.kinterest.datastore.Datastore
import kotlin.reflect.KClass

interface KIEntity<out ID:Any> {
    val id : ID
    val _store : Datastore
    val _meta : KIEntityMeta

    fun<V> getValue(property: PropertyMeta) : V?
    fun<V> setValue(property: PropertyMeta, v:V?)
    fun asTransient() : KITransientEntity<ID>

    fun _equals(o:Any?) : Boolean = o is KIEntity<*> && o._meta == _meta && o.id==id
    fun _hashCode() : Int = id.hashCode()
    fun _toString() : String = """${_meta.name}($id)"""
}

interface KIEntityMeta {
    val name : String
    val type : KClass<*>
    val idType : PropertyMeta
    val idGenerated : Boolean
    val parentMeta : KIEntityMeta?
    val baseMeta : KIEntityMeta
    val properties : Map<String,PropertyMeta>

    fun<ID:Any> instance(_store:Datastore, id:Any) : KIEntity<ID>

    val hierarchy : List<KIEntityMeta> get() = (parentMeta?.hierarchy ?: listOf()) + this
    val metaBlock : MetaInfo get() = MetaInfo(this, hierarchy, mapOf())

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

sealed class PropertyMeta(val name : String, val type : KClass<*>, val nullable : Boolean, val readOnly : Boolean)
sealed class SimplePropertyMeta(name: String, type: KClass<*>, nullable : Boolean, readOnly : Boolean) : PropertyMeta(name, type, nullable, readOnly)
class StringPropertyMeta(name: String, nullable : Boolean, readOnly : Boolean) : SimplePropertyMeta(name, String::class, nullable, readOnly)
sealed class NumberPropertyMeta(name: String, type: KClass<*>, nullable : Boolean, readOnly : Boolean) : SimplePropertyMeta(name, type, nullable, readOnly)
class IntPropertyMeta(name: String, nullable : Boolean, readOnly : Boolean) : NumberPropertyMeta(name, Int::class, nullable, readOnly)
class LongPropertyMeta(name: String, nullable : Boolean, readOnly : Boolean) : NumberPropertyMeta(name, Long::class, nullable, readOnly)
class DoublePropertyMeta(name: String, nullable : Boolean, readOnly : Boolean) : NumberPropertyMeta(name, Double::class, nullable, readOnly)
class FloatPropertyMeta(name: String, nullable: Boolean, readOnly: Boolean) : NumberPropertyMeta(name, Float::class, nullable, readOnly)
sealed class CollectionPropertyMeta(name: String, type: KClass<*>, nullable : Boolean, readOnly : Boolean, val contained:PropertyMeta) : PropertyMeta(name, type, nullable, readOnly)
class ArrayPropertyMeta(name: String, nullable : Boolean, readOnly : Boolean, contained:PropertyMeta) : CollectionPropertyMeta(name, Array<Any>::class, nullable, readOnly, contained)
class ReferenceProperty(name: String, type: KClass<*>, nullable : Boolean, readOnly : Boolean) : PropertyMeta(name, type, nullable, readOnly)

sealed class DateOrTimeProperty(name : String, type : KClass<*>, nullable : Boolean, readOnly : Boolean) : PropertyMeta(name, type, nullable, readOnly)
class LocalDateProperty()

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

    override fun _equals(o:Any?) : Boolean = o is KIEntity<*> && o._meta == _meta && o.id==id
    override fun _toString() : String = """${this::class.simpleName}($properties)"""
}

var<ID:Any> KITransientEntity<ID>._id : ID?
    get() = if(hasID) id else null
    set(value) { properties["id"] = value }