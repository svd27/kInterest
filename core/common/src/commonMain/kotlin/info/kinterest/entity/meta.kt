package info.kinterest.entity

import info.kinterest.datastore.Datastore
import kotlin.reflect.KClass

interface KIEntityMeta {
    val name : String
    val type : KClass<*>
    val idType : PropertyMeta
    val idGenerated : Boolean
    val parentMeta : KIEntityMeta?
    val baseMeta : KIEntityMeta
    val properties : Map<String,PropertyMeta>

    fun<ID:Any> instance(_store: Datastore, id:Any) : KIEntity<ID>

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

sealed class DateOrTimeProperty(name : String, type : KClass<*>, nullable : Boolean, readOnly : Boolean) : PropertyMeta(name, type, nullable, readOnly)
class LocalDateProperty()

class ReferenceProperty(name: String, type: KClass<*>, nullable : Boolean, readOnly : Boolean) : PropertyMeta(name, type, nullable, readOnly)
sealed class RelationProperty(name: String, type: KClass<*>, nullable: Boolean, readOnly: Boolean) : PropertyMeta(name, type, nullable, readOnly)
class SingleRelationProperty(name: String, type: KIEntityMeta, nullable: Boolean, readOnly: Boolean) :  RelationProperty(name, type.type, nullable, readOnly)
sealed class CollectionRelationProperty(name: String, type: KClass<*>, nullable: Boolean, readOnly: Boolean, val mutable:Boolean, val contained: KIEntityMeta) :  RelationProperty(name, type, nullable, readOnly)
class SetRelationProperty(name: String, nullable: Boolean, readOnly: Boolean, mutable:Boolean, contained: KIEntityMeta) :
        CollectionRelationProperty(name, if(mutable) MutableSet::class else Set::class, nullable, readOnly, mutable, contained)
class ListRelationProperty(name: String, nullable: Boolean, readOnly: Boolean, mutable:Boolean, contained: KIEntityMeta) :
        CollectionRelationProperty(name, if(mutable) MutableList::class else List::class, nullable, readOnly, mutable, contained)
class MapRelationProperty(name: String, nullable: Boolean, readOnly: Boolean, mutable:Boolean, contained: KIEntityMeta, keyType: KClass<*>) :
        CollectionRelationProperty(name, if(mutable) MutableMap::class else Map::class, nullable, readOnly, mutable, contained)

