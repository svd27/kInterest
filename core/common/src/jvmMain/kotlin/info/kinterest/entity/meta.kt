package info.kinterest.entity

interface KIEntityMetaJvm : KIEntityMeta {
    override val name get() = type.qualifiedName!!
}
