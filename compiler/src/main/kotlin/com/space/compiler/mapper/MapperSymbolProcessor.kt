package com.space.compiler.mapper

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.space.annotations.*
import com.space.compiler.extensions.*
import com.space.compiler.extensions.capitalize
import com.space.compiler.extensions.decapitalize
import com.space.compiler.extensions.isClass
import com.space.compiler.extensions.isEnum
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class MapperSymbolProcessor(private val logger: KSPLogger, private val codeGenerator: CodeGenerator) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val unresolvedSymbols = mutableListOf<KSAnnotated>()
        resolver.getNewFiles().forEach { file ->
            val dtoDeclarations = file.filterAnnotatedWith(DTO::class)
            val domainDeclarations = file.filterAnnotatedWith(DomainModel::class).toMutableList()

            dtoDeclarations.mapNotNullToDtoAndDomainPairs(domainDeclarations, resolver)
                .forEach { (dtoDeclaration, domainDeclaration) ->
                    try {
                        domainDeclarations.remove(domainDeclaration)
                        generateMapperFile(resolver, file, dtoDeclaration, domainDeclaration)
                    } catch (e: FileNotGeneratedException) {
                        unresolvedSymbols.addAll(listOf(dtoDeclaration, domainDeclaration))
                    }
                }

            domainDeclarations.forEach {
                logger.warn(
                    "There was found Domain model ${it.simpleName.asString()}, but there was no DTO model, so it was skipped",
                    it
                )
            }
        }
        return unresolvedSymbols
    }

    private fun List<KSClassDeclaration>.mapNotNullToDtoAndDomainPairs(
        domainDeclarations: List<KSClassDeclaration>,
        resolver: Resolver
    ) =
        mapNotNull { dtoDeclaration ->
            val dtoDeclarationName = dtoDeclaration.withoutSuffix(MapperModelType.DTO.suffix)
            dtoDeclaration.getAnnotationOrNull(MapsTo::class)?.let {
                val mapsToClass = (it.arguments.first().value as? KSType)?.toClassName()
                val domainDeclaration = resolver.getClassDeclarationByName(mapsToClass.toString())!!
                return@mapNotNull dtoDeclaration to domainDeclaration
            }
            domainDeclarations.find { it.withoutSuffix(MapperModelType.Domain.suffix) == dtoDeclarationName }
                ?.let { domainDeclaration -> dtoDeclaration to domainDeclaration } ?: run {
                logger.warn(
                    "There was found DTO model ${dtoDeclarationName}, but there was no Domain model, so it was skipped",
                    dtoDeclaration
                )
                null
            }
        }

    private fun generateMapperFile(
        resolver: Resolver,
        file: KSFile,
        dtoDeclaration: KSClassDeclaration,
        domainDeclaration: KSClassDeclaration
    ) {
        val fileName = dtoDeclaration.withoutSuffix(MapperModelType.DTO.suffix)
        val filePackage = dtoDeclaration.packageName.asString() + MAPPER_PACKAGE_NAME
        val className = "$filePackage.${fileName}Mapper"
        val classDeclaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(className))
        if (classDeclaration != null) return

        val dtoDeclarationObject = DeclarationObject(dtoDeclaration, MapperModelType.DTO)
        val domainDeclarationObject = DeclarationObject(domainDeclaration, MapperModelType.Domain)

        val typeSpec = TypeSpec.classBuilder("${fileName}Mapper")
            .addOriginatingKSFile(file)
            .apply {
                if (dtoDeclaration.classKind == ClassKind.CLASS) {
                    generateMapperFunction(dtoDeclarationObject, domainDeclarationObject)
                    generateMapperFunction(domainDeclarationObject, dtoDeclarationObject)
                } else {
                    generateEnumMapperFunction(dtoDeclarationObject, domainDeclarationObject)
                    generateEnumMapperFunction(domainDeclarationObject, dtoDeclarationObject)
                }
            }
            .build()

        FileSpec.builder(filePackage, "${fileName}Mapper_Gen")
            .addType(typeSpec)
            .build()
            .writeTo(codeGenerator, aggregating = false)
    }

    private fun TypeSpec.Builder.generateMapperFunction(
        from: DeclarationObject,
        to: DeclarationObject
    ) {
        val functionName = getFunctionName(from, to)

        val funSpecBuilder = FunSpec.builder(functionName)
            .addParameter(from.modelType.modelName, from.declaration.toClassName())
            .returns(to.declaration.toClassName())

        val codeBuilder = CodeBlock.builder().add("return·%T(\n", to.declaration.toClassName())

        to.declaration.primaryConstructor?.parameters?.forEach { param ->
            val paramName = param.name?.asString() ?: return@forEach

            val fromParam = from.declaration.primaryConstructor?.parameters
                ?.firstOrNull {
                    it.name?.asString() == paramName || paramName == from.getFieldName(it, to.declaration)
                }

            val mappingCode = when {
                param.getExternalMapper() != null -> {
                    generateExternalMapperCode(
                        paramName,
                        param,
                        from,
                        to,
                        functionName,
                        funSpecBuilder
                    )
                }

                fromParam != null && fromParam.type.resolve() == param.type.resolve() -> "${from.modelType.modelName}.$fromParam"
                else -> generateLambdaMapperCode(from, to, param, paramName, funSpecBuilder)
            }

            codeBuilder.add("$paramName = $mappingCode,\n")
        }

        codeBuilder.add(")")

        funSpecBuilder.addCode(codeBuilder.build())
        addFunction(funSpecBuilder.build())
    }

    private fun generateExternalMapperCode(
        paramName: String,
        param: KSValueParameter,
        from: DeclarationObject,
        to: DeclarationObject,
        functionName: String,
        funSpec: FunSpec.Builder
    ): String {
        val externalMapper = param.getExternalMapper() ?: return ""
        val externalClazzName = externalMapper.toClassName().simpleName.removeSuffix(to.modelType.suffix)
        val packageName = param.type.resolve().toClassName().packageName + MAPPER_PACKAGE_NAME
        val parameterName = externalClazzName.decapitalize() + "Mapper"
        funSpec.addParameter(
            parameterName,
            ClassName(packageName, "${externalClazzName}Mapper")
        )
        return "${parameterName}.$functionName(${from.modelType.modelName}.$paramName)"
    }

    private fun generateLambdaMapperCode(
        from: DeclarationObject,
        to: DeclarationObject,
        param: KSValueParameter,
        paramName: String,
        funSpecBuilder: FunSpec.Builder
    ): String {
        val fromParam = from.declaration.primaryConstructor?.parameters
            ?.firstOrNull {
                it.name?.asString() == paramName || paramName == from.getFieldName(it, to.declaration)
            }
        val fromParamType = fromParam?.type?.resolve()?.toTypeName() ?: return ""
        val fromParamName = fromParam.name?.asString()

        val returnType = param.type.resolve().toTypeName()

        val functionType = LambdaTypeName.get(
            parameters = arrayOf(fromParamType),
            returnType = returnType
        )

        funSpecBuilder.addParameter("get${paramName.capitalize()}", functionType)
        return "get${paramName.capitalize()}.invoke(${from.modelType.modelName}.$fromParamName)"
    }

    private fun TypeSpec.Builder.generateEnumMapperFunction(
        from: DeclarationObject, to: DeclarationObject
    ) {
        val functionName = getFunctionName(from, to)
        val funSpec = FunSpec.builder(functionName)
            .addParameter(from.modelType.modelName, from.declaration.toClassName())
            .returns(to.declaration.toClassName())
        val clazzName = from.declaration.annotations.first().arguments.first().value.toString().substringAfterLast(".")
        val mapWith = enumValueOf<MapWith>(clazzName)
        funSpec.addCode(
            CodeBlock.builder()
                .add("return ${to.declaration.toClassName()}.values().first { it.${mapWith.identifier} == ${from.modelType.modelName}.${mapWith.identifier}  }")
                .build()
        )
        addFunction(funSpec.build())
    }

    private fun KSValueParameter.getExternalMapper(): KSType? {
        val type = type.resolve()
        return if (type.isEnum() || type.isClass()) {
            type
        } else null
    }

    private fun getFunctionName(from: DeclarationObject, to: DeclarationObject) =
        "map${from.modelType.suffix}To${to.modelType.suffix}"

    data class DeclarationObject(
        val declaration: KSClassDeclaration,
        val modelType: MapperModelType
    ) {
        fun getFieldName(ksValueParameter: KSValueParameter, other: KSClassDeclaration): String? {
            val parameterName = ksValueParameter.name?.asString()
            return when (modelType) {
                MapperModelType.Domain -> other.primaryConstructor!!.parameters.firstOrNull {
                    it.annotations.any { annotation ->
                        annotation.shortName.asString() == DomainFieldName::class.simpleName &&
                                annotation.arguments.first().value?.toString() == parameterName
                    }
                }?.name?.asString()

                MapperModelType.DTO -> ksValueParameter.annotations.firstOrNull {
                    it.annotationType.resolve().declaration.simpleName.asString() == DomainFieldName::class.simpleName
                }?.arguments?.first()?.value as? String
            }
        }
    }

    class FileNotGeneratedException(s: String) : Exception(s)

    companion object {
        private const val MAPPER_PACKAGE_NAME = ".mappers"
    }
}