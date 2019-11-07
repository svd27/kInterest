package info.kinterest.generator

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import info.kinterest.annotations.Entity
import java.nio.file.Paths
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@KotlinPoetMetadataPreview
@AutoService(javax.annotation.processing.Processor::class)
@SupportedAnnotationTypes("info.kinterest.annotations.Entity")
@SupportedOptions(Processor.KAPT_KOTLIN_GENERATED_OPTION_NAME, "kapt.verbose", "targets")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
class Processor : AbstractProcessor() {
    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        @KotlinPoetMetadataPreview
        val generators : MutableMap<String,Generator> = mutableMapOf("jvm" to JvmGenerator())
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        println("!!!processor!!!")
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "!!!processor!!!")
        println("args ${processingEnv.options}")
        val targets = processingEnv.options["targets"]!!
        val outDir = processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]!!

        targets.split(',').forEach { target ->
            processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "$target got elements $annotations ")
            roundEnv.getElementsAnnotatedWith(Entity::class.java).map { it.toTypeElementOrNull() }.filterNotNull().forEach { source ->
                processingEnv.note("processing $source")
                generators[target]?.let {
                    processingEnv.note("using generator $it")
                    it.generate(source, processingEnv, roundEnv)?.let { f: FileSpec ->
                        f.writeTo(System.out)
                        processingEnv.note("Path: $outDir")
                        f.writeTo(Paths.get(outDir))
                    }
                }
            }
        }

        return false
    }

    fun Element.toTypeElementOrNull(): TypeElement? {
        if (this !is TypeElement) {
            processingEnv.error("Invalid element type, class expected", this)
            return null
        }

        return this
    }
}

fun ProcessingEnvironment.note(msg:Any?) = messager.printMessage(Diagnostic.Kind.NOTE, "$msg")
fun ProcessingEnvironment.error(msg:Any?, e:Element?=null) = if(e!=null) messager.printMessage(Diagnostic.Kind.ERROR, "$msg", e) else messager.printMessage(Diagnostic.Kind.ERROR, "$msg")