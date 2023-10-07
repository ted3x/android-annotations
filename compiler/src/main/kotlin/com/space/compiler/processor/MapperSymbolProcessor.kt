package com.space.compiler.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.space.annotations.DTO
import com.space.annotations.DomainModel
import com.space.annotations.MapWith
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
        resolver.getNewFiles().forEach { file ->
            val dtoDeclarations = file.filterAnnotatedWith(DTO::class)
            val domainDeclarations = file.filterAnnotatedWith(DomainModel::class)

            dtoDeclarations.mapNotNullToDtoAndDomainPairs(domainDeclarations)
                .forEach { (dtoDeclaration, domainDeclaration) ->
                    generateMapperFile(file, dtoDeclaration, domainDeclaration)
                }
        }
        return emptyList()
    }

    private fun List<KSClassDeclaration>.mapNotNullToDtoAndDomainPairs(domainDeclarations: List<KSClassDeclaration>) =
        mapNotNull { dtoDeclaration ->
            val dtoDeclarationName = dtoDeclaration.withoutSuffix(DTO_SUFFIX)
            domainDeclarations.find { it.withoutSuffix(DOMAIN_SUFFIX) == dtoDeclarationName }
                ?.let { domainDeclaration -> dtoDeclaration to domainDeclaration }
        }

    private fun generateMapperFile(
        file: KSFile,
        dtoDeclaration: KSClassDeclaration,
        domainDeclaration: KSClassDeclaration
    ) {
        val fileName = dtoDeclaration.withoutSuffix(DTO_SUFFIX)
        val filePackage = dtoDeclaration.packageName.asString() + MAPPER_PACKAGE_NAME

        val dtoDeclarationObject = DeclarationObject(dtoDeclaration, DTO_MODEL, DTO_SUFFIX)
        val domainDeclarationObject = DeclarationObject(domainDeclaration, DOMAIN_MODEL, DOMAIN_SUFFIX)

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

    private fun TypeSpec.Builder.generateMapperFunction(from: DeclarationObject, to: DeclarationObject) {
        val functionName = getFunctionName(from, to)

        val funSpecBuilder = FunSpec.builder(functionName)
            .addParameter(from.name, from.declaration.toClassName())
            .returns(to.declaration.toClassName())

        val codeBuilder = CodeBlock.builder().add("return·%T(\n", to.declaration.toClassName())

        to.declaration.primaryConstructor?.parameters?.forEach { param ->
            val paramName = param.name?.asString() ?: return@forEach

            val fromParam = from.declaration.primaryConstructor?.parameters
                ?.firstOrNull { it.name?.asString() == paramName }

            val mappingCode = when {
                param.getExternalMapper() != null -> generateExternalMapperCode(paramName, param, from, to, functionName, funSpecBuilder)
                fromParam != null && fromParam.type.resolve() == param.type.resolve() -> "${from.name}.$paramName"
                else -> generateLambdaMapperCode(from, param, paramName, funSpecBuilder)
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
        val externalClazzName = externalMapper.toClassName().simpleName.removeSuffix(to.suffix)
        funSpec.addParameter(
            externalClazzName.decapitalize(),
            ClassName("com.space.test.mappers", "${externalClazzName}Mapper")
        )
        return "${externalClazzName.decapitalize()}.$functionName(${from.name}.$paramName)"
    }

    private fun generateLambdaMapperCode(
        from: DeclarationObject,
        param: KSValueParameter,
        paramName: String,
        funSpecBuilder: FunSpec.Builder
    ): String {
        val fromParamType = from.declaration.primaryConstructor?.parameters
            ?.firstOrNull { it.name?.asString() == paramName }
            ?.type?.resolve()?.toTypeName() ?: return ""

        val returnType = param.type.resolve().toTypeName()

        val functionType = LambdaTypeName.get(
            parameters = arrayOf(fromParamType),
            returnType = returnType
        )

        funSpecBuilder.addParameter("get${paramName.capitalize()}", functionType)
        return "get${paramName.capitalize()}.invoke(${from.name}.$paramName)"
    }

    private fun TypeSpec.Builder.generateEnumMapperFunction(
        from: DeclarationObject, to: DeclarationObject
    ) {
        val functionName = getFunctionName(from, to)
        val funSpec = FunSpec.builder(functionName)
            .addParameter(from.name, from.declaration.toClassName())
            .returns(to.declaration.toClassName())
        val clazzName = from.declaration.annotations.first().arguments.first().value.toString().substringAfterLast(".")
        val mapWith = enumValueOf<MapWith>(clazzName)
        funSpec.addCode(
            CodeBlock.builder()
                .add("return ${to.declaration.toClassName()}.values().first { it.${mapWith.identifier} == ${from.name}.${mapWith.identifier}  }")
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

    private fun getFunctionName(from: DeclarationObject, to: DeclarationObject) = "map${from.suffix}To${to.suffix}"

    data class DeclarationObject(val declaration: KSClassDeclaration, val name: String, val suffix: String)

    companion object {
        private const val DTO_SUFFIX = "Dto"
        private const val DTO_MODEL = "dtoModel"

        private const val DOMAIN_SUFFIX = "Domain"
        private const val DOMAIN_MODEL = "domainModel"

        private const val MAPPER_PACKAGE_NAME = ".mappers"
    }
}