package info.kinterest.projection

import info.kinterest.entity.KIEntity

sealed class Projection<ID:Any,E:KIEntity<ID>>(val name:String, val parent:Projection<ID,E>?=null)
class EntityProjection<ID:Any,E:KIEntity<ID>>(name: String, parent: Projection<ID, E>?) : Projection<ID,E>(name, parent)