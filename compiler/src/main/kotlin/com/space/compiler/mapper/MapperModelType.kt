package com.space.compiler.mapper

enum class MapperModelType(val suffix: String, val modelName: String) {
    DTO("Dto", "dtoModel"),
    Domain("DomainModel", "domainModel")
}