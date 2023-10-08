package com.space.compiler.featurecomponent

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

class FeatureComponentSymbolProcessor(private val logger: KSPLogger, private val codeGenerator: CodeGenerator) :
    SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation("com.space.annotations.FeatureComponent")
        val ret = symbols.filter { !it.validate() }.toList()
        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(Visitor(), Unit) }
        return ret
    }

    inner class Visitor() : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            super.visitClassDeclaration(classDeclaration, data)
            if (classDeclaration.classKind != ClassKind.INTERFACE) {
                logger.warn("@FeatureComponent can only be applied to interface", classDeclaration)
            }

            val annotation = classDeclaration.annotations.first { it.shortName.asString() == "FeatureComponent" }
            val qualifier = (annotation.arguments.first().value as KSType).toClassName()
            val featureModule = (annotation.arguments[1].value as KSType).toClassName()
            val classNameApi = classDeclaration.toClassName()
            val className = classDeclaration.simpleName.asString().removeSuffix("Api")
            val typeSpec = TypeSpec.classBuilder(className).apply {
                addSuperinterface(classDeclaration.toClassName())
                classDeclaration.containingFile?.let { addOriginatingKSFile(it) }
                classDeclaration.getAllFunctions().filter { it.functionKind == FunctionKind.MEMBER && it.isAbstract }
                    .forEach { it.accept(FunctionVisitor(this, className, qualifier, featureModule), Unit) }
                classDeclaration.getAllProperties().filter { it.modifiers.contains(Modifier.ABSTRACT) }
                    .forEach { it.accept(FunctionVisitor(this, className, qualifier, featureModule), Unit) }
            }
            val parameterizedType = ClassName("com.space.test.feature_component", "SingletonCountHolder")
                .parameterizedBy(classNameApi)

            val companionBuilder = TypeSpec.companionObjectBuilder()
                .superclass(parameterizedType)
                .addSuperclassConstructorParameter(CodeBlock.of("::$className"))

            typeSpec.addType(companionBuilder.build())
            FileSpec.builder(classDeclaration.packageName.asString(), "${className}_Gen")
                .addType(typeSpec.build())
                .build().writeTo(codeGenerator, false)
        }
    }

    inner class FunctionVisitor(
        private val typeSpec: TypeSpec.Builder,
        private val className: String,
        private val qualifier: ClassName,
        private val featureModule: ClassName
    ) : KSVisitorVoid() {

        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
            super.visitPropertyDeclaration(property, data)
            val propertySpec = PropertySpec.builder(
                property.simpleName.asString(),
                featureModule,
                KModifier.OVERRIDE
            ).initializer("$featureModule(featureQualifer())")
            typeSpec.addProperty(propertySpec.build())
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            super.visitFunctionDeclaration(function, data)
            when (function.simpleName.asString()) {
                "featureQualifer" -> typeSpec.generateFeatureQualifierFunction(function, qualifier)
                "featureDestroy" -> typeSpec.generateFeatureDestroyFunction(function, className)
                else -> {
                    typeSpec.addFunction(
                        FunSpec.builder(function.simpleName.asString()).apply {
                            function.returnType?.resolve()?.let {
                                returns(it.toClassName())
                                addCode(
                                    CodeBlock.builder().add("return ${it.toClassName()}(featureQualifer())").build()
                                )
                            }
                        }.addModifiers(KModifier.OVERRIDE).build()
                    )
                }
            }
        }

        private fun TypeSpec.Builder.generateFeatureDestroyFunction(
            function: KSFunctionDeclaration,
            className: String
        ) {
            addFunction(
                FunSpec.builder(function.simpleName.asString()).apply {
                    function.returnType?.resolve()?.let {
                        returns(it.toClassName())
                        addCode(
                            CodeBlock.builder().withIndent {
                                add(
                                    "$className.reset()\n" +
                                            "if (!$className.checkIfNotNull())\n" +
                                            "    featureModule.destroy()"
                                )
                            }.build()
                        )
                    }
                }.addModifiers(KModifier.OVERRIDE).build()
            )
        }

        private fun TypeSpec.Builder.generateFeatureQualifierFunction(
            function: KSFunctionDeclaration,
            qualifier: ClassName
        ) {
            addFunction(
                FunSpec.builder(function.simpleName.asString()).apply {
                    function.returnType?.resolve()?.let {
                        returns(it.toClassName())
                        addCode(CodeBlock.builder().add("return $qualifier::class.java.getFeatureQualifer()").build())
                    }
                }.addModifiers(KModifier.OVERRIDE).build()
            )
        }
    }
}