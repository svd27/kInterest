package info.kinterest.entity

import info.kinterest.DONTDOTHIS
import info.kinterest.datastore.Datastore
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.serializer
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
    val metaBlock : MetaInfo get() = MetaInfo(this, hierarchy, mapOf(), mapOf())

    //TODO: so same meta loaded with different classloader will not equal on jvm
    fun _equals(other:Any?) : Boolean = other is KIEntityMeta && other.type == type
    fun _hashCode() : Int = type.hashCode()
    fun _toString() : String = "Meta($name, $type)"

    companion object {
        const val TYPEKEY = "type"
        const val TYPESKEY = "types"
        const val RELATIONSKEY = "relations"
        const val OUTGOING = "outgoing"
        const val INCOMING = "incoming"
    }
}

data class MetaInfo(val type: KIEntityMeta, val types:List<KIEntityMeta>, val outgoing:Map<RelationProperty,List<RelationTo>>, val incoming : Map<String,RelationFrom> ) {
    fun asMap() : Map<String,Any> = mapOf(KIEntityMeta.TYPEKEY to type.name, KIEntityMeta.TYPESKEY to types.map { it.name }, KIEntityMeta.RELATIONSKEY to mapOf(KIEntityMeta.OUTGOING to outgoing, KIEntityMeta.INCOMING to incoming))
}

sealed class PropertyMeta(val name : String, val type : KClass<*>, val nullable : Boolean, val readOnly : Boolean) {
    @InternalSerializationApi
    @Suppress("UNCHECKED_CAST")
    open fun serializer() : KSerializer<Any> = (if(nullable) type.serializer().nullable else type.serializer()) as KSerializer<Any>

    override fun equals(other: Any?): Boolean = other is PropertyMeta && other.name == name && other.type == type

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "PropertyMeta($name, $type, nullable=$nullable, readOnly: $readOnly)"
}
sealed class SimplePropertyMeta(name: String, type: KClass<*>, nullable : Boolean, readOnly : Boolean) : PropertyMeta(name, type, nullable, readOnly)
class StringPropertyMeta(name: String, nullable : Boolean, readOnly : Boolean) : SimplePropertyMeta(name, String::class, nullable, readOnly)
sealed class NumberPropertyMeta(name: String, type: KClass<*>, nullable : Boolean, readOnly : Boolean) : SimplePropertyMeta(name, type, nullable, readOnly)
class IntPropertyMeta(name: String, nullable : Boolean, readOnly : Boolean) : NumberPropertyMeta(name, Int::class, nullable, readOnly) {
}
class LongPropertyMeta(name: String, nullable : Boolean, readOnly : Boolean) : NumberPropertyMeta(name, Long::class, nullable, readOnly) {
}

class DoublePropertyMeta(name: String, nullable : Boolean, readOnly : Boolean) : NumberPropertyMeta(name, Double::class, nullable, readOnly)
class FloatPropertyMeta(name: String, nullable: Boolean, readOnly: Boolean) : NumberPropertyMeta(name, Float::class, nullable, readOnly)
sealed class CollectionPropertyMeta(name: String, type: KClass<*>, nullable : Boolean, readOnly : Boolean, val contained:PropertyMeta) : PropertyMeta(name, type, nullable, readOnly)

sealed class DateOrTimeProperty(name : String, type : KClass<*>, nullable : Boolean, readOnly : Boolean) : PropertyMeta(name, type, nullable, readOnly)
class LocalDateProperty()

class ReferenceProperty(name: String, type: KClass<*>, nullable : Boolean, readOnly : Boolean) : PropertyMeta(name, type, nullable, readOnly) {
    @InternalSerializationApi
    override fun serializer(): KSerializer<Any> = DONTDOTHIS("not supported yet")
}

sealed class RelationProperty(name: String, type: KClass<*>, nullable: Boolean, readOnly: Boolean, val contained: KIEntityMeta) : PropertyMeta(name, type, nullable, readOnly)
class SingleRelationProperty(name: String, type: KIEntityMeta, nullable: Boolean, readOnly: Boolean) :  RelationProperty(name, type.type, nullable, readOnly, type)
sealed class CollectionRelationProperty(name: String, type: KClass<*>, nullable: Boolean, readOnly: Boolean, val mutable:Boolean, contained: KIEntityMeta) :  RelationProperty(name, type, nullable, readOnly, contained)
class SetRelationProperty(name: String, nullable: Boolean, readOnly: Boolean, mutable:Boolean, contained: KIEntityMeta) :
        CollectionRelationProperty(name, if(mutable) MutableSet::class else Set::class, nullable, readOnly, mutable, contained)
class ListRelationProperty(name: String, nullable: Boolean, readOnly: Boolean, mutable:Boolean, contained: KIEntityMeta) :
        CollectionRelationProperty(name, if(mutable) MutableList::class else List::class, nullable, readOnly, mutable, contained)

