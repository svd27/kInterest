package info.kinterest.projection

import info.kinterest.entity.KIEntity
import info.kinterest.projection.paging.Page
import info.kinterest.projection.paging.Paging
import info.kinterest.projection.sorting.Ordering

sealed class Projection<ID:Any,E:KIEntity<ID>>(val name:String, val parent:Projection<ID,E>?=null)
class ParentProjection<ID:Any,E:KIEntity<ID>>(val projections: Map<String,Projection<ID, E>>) : Projection<ID,E>("root", null) {
    operator fun plus(p:Projection<ID,E>) : ParentProjection<ID,E> = ParentProjection(projections + (p.name to p))
}
class EntityProjection<ID:Any,E:KIEntity<ID>>(name: String, val paging: Paging, val order:Ordering<ID,E>? = null, parent: Projection<ID, E>) : Projection<ID,E>(name, parent)

sealed class ProjectionResult<K : Any, E : KIEntity<K>>(open val projection: Projection<K,E>) {
    val name
        get() = projection.name

    override fun toString(): String = "${this::class.simpleName}($name)"
}

class ParentProjectionResult<ID:Any,E:KIEntity<ID>>(override val projection: ParentProjection<ID, E>, val results:Map<String,ProjectionResult<ID,E>>) : ProjectionResult<ID,E>(projection)

class ReloadProjectionResult<K : Any, E : KIEntity<K>>(projection: Projection<K,E>) : ProjectionResult<K,E>(projection)
class EntityProjectionResult<K : Any,E : KIEntity<K>>(override val projection: EntityProjection<K, E>, val page: Page<E, K>) : ProjectionResult<K,E>(projection)