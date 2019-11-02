package info.kinterest.entity

data class RelationTrace(val relation:RelationProperty, val fromType: KIEntityMeta, val fromId:Any, val fromDatastore: String, val toType:KIEntityMeta, val toId:Any, val toDatastore: String)
data class RelationTo(val relation:RelationProperty, val toType:KIEntityMeta, val toId:Any, val toDatastore: String)

abstract class RelationSet<ID:Any, out E:KIEntity<ID>>() : AbstractSet<E>() {
    abstract val relations : Set<RelationTo>
    override val size: Int
        get() = relations.size
}

abstract class RelationList<ID:Any, out E:KIEntity<ID>>(val e:KIEntity<Any>) : AbstractList<E>() {
    abstract val relations : Set<RelationTo>
    override val size: Int
        get() = relations.size
}