package info.kinterest.entity

data class RelationTrace(val relation:RelationProperty, val fromType: KIEntityMeta, val fromId:Any, val fromDatastore: String, val toType:KIEntityMeta, val toId:Any, val toDatastore: String) {
    val asToTrace : RelationTo by lazy {
        RelationTo(fromType, relation, toType, toId, toDatastore)
    }

    val asFromTrace : RelationFrom by lazy {
        RelationFrom(relation, fromType, fromId, fromDatastore)
    }
}

/*
@Serializer(forClass = RelationTo::class)
object RelationToSerializer : KSerializer<RelationTo> {
    override val descriptor: SerialDescriptor = object : SerialClassDescImpl("RelationTo") {
        init {
            addElement("relation")
            addElement("toType")
            addElement("toId")
            addElement("toDatastore")
        }
    }

    @ImplicitReflectionSerializer
    override fun serialize(encoder: Encoder, obj: RelationTo) {
        val comp = encoder.beginStructure(descriptor)
        comp.encodeStringElement(descriptor, 0, obj.relation.name)
        comp.encodeStringElement(descriptor, 1, obj.toType.name)
        val l = encoder.beginCollection(descriptor, 2)
        l.encodeStringElement(descriptor, 0, obj.fromType.name)
        l.encodeSerializableElement(descriptor, 1, obj.fromType.idType.serializer() as KSerializer<Any>, obj.toId)
        l.endStructure(descriptor)
        comp.encodeStringElement(descriptor, 4, obj.toDatastore)
        comp.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): RelationTo {
        val dec = decoder.beginStructure(descriptor)
        var fromType : KIEntityMeta?
        var relation : String?
        var toType : String?
        var toId : Any?
        var toDatastore : String?

        loop@ while (true) {
            when(val idx = dec.decodeElementIndex(descriptor)) {
                CompositeDecoder.READ_DONE -> break@loop
                0 ->  relation = dec.decodeStringElement(descriptor, idx)
                1 -> toType = dec.decodeStringElement(descriptor, idx)
            }
        }

    }
}
*/

//@Serializable(with = RelationToSerializer::class)
data class RelationTo(val fromType: KIEntityMeta, val relation:RelationProperty, val toType:KIEntityMeta, val toId:Any, val toDatastore: String) {
    fun toMap() : Map<String,Any> = mapOf("relation" to relation.name, "toType" to toType.name, "toId" to toId, "toDatastore" to toDatastore)
    fun asRelationFrom(e:KIEntity<*>) : RelationFrom = RelationFrom(relation, e._meta, e.id, e._store.name)
    companion object {
        fun fromMap(type: KIEntityMeta, source: Map<String, Any>, metaLookup: (String) -> KIEntityMeta): RelationTo = source.run {

            val rel = type.properties[source["relation"].toString()] as? RelationProperty
                    ?: throw IllegalStateException()
            RelationTo(type,
                    rel,
                    metaLookup(source["toType"].toString()),
                    source["toId"]!!,
                    source["toDatastore"].toString()
            )
        }
    }
}

data class RelationFrom(val relation:RelationProperty, val fromType:KIEntityMeta, val fromId: Any, val fromDatastore: String) {
    fun toMap() : Map<String,Any> = mapOf("relation" to relation.name, "fromType" to fromType.name, "fromId" to fromId, "fromDatastore" to fromDatastore)

    companion object {
        fun fromMap(source: Map<String, Any>, metaLookup: (String) -> KIEntityMeta) : RelationFrom = source.run {
            val meta = metaLookup(source["fromType"].toString())

            RelationFrom(
                    meta.properties[source["relation"].toString()] as? RelationProperty ?: throw IllegalStateException(),
                    meta,
                    source["fromId"]!!,
                    source["fromDatastore"].toString()
            )
        }
    }
}

