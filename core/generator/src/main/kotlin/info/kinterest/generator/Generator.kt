package info.kinterest.generator

import com.squareup.kotlinpoet.FileSpec
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

abstract class Generator {
    abstract fun generate(type: TypeElement, processingEnvironment: ProcessingEnvironment,
                          roundEnvironment: RoundEnvironment) : FileSpec?
}
