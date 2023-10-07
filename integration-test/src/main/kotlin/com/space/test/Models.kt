package com.space.test

import com.space.annotations.DTO
import com.space.annotations.DomainModel
import com.space.annotations.MapWith

@DTO
data class ModelOneDto(val id: String, val username: String)

@DomainModel
data class ModelOneDomain(val id: String, val username: String)














@DTO
enum class ModelTwoEnumDto {
    Enabled,
    Disabled
}

@DTO
data class ModelTwoDto(val id: String, val enum: ModelTwoEnumDto, val test: Int)

@DomainModel
enum class ModelTwoEnumDomain {
    Enabled,
    Disabled
}

@DomainModel
data class ModelTwoDomain(val id: String, val enum: ModelTwoEnumDomain, val test: String)

@DTO(MapWith.Value)
enum class ModelTwoEnumValueDto(val value: Int) {
    Enabled_(1),
    Disabled_(2)
}

@DomainModel(MapWith.Value)
enum class ModelTwoEnumValueDomain(val value: Int) {
    Enabled(1),
    Disabled(2)
}

@DTO
data class ModelTwoWithAnotherModelDto(val id: String, val model: ModelOneDto, val test: Int)

@DomainModel
data class ModelTwoWithAnotherModelDomain(val id: String, val model: ModelOneDomain, val test: String)