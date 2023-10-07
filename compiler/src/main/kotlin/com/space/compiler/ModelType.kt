package com.space.compiler

enum class ModelType(val suffix: String, val modelName: String) {
    DTO("Dto", "dtoModel"),
    Domain("DomainModel", "domainModel")
}