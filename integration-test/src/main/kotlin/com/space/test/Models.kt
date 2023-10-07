package com.space.test

import com.space.annotations.DTO
import com.space.annotations.DomainModel
import com.space.annotations.MapWith

@DTO
data class ModelOneDto(val id: String, val username: String)

@DomainModel
data class ModelOneDomainModel(val id: String, val username: String)














@DTO
enum class ModelTwoEnumDto {
    Enabled,
    Disabled
}

@DTO
data class ModelTwoDto(val id: String, val enum: ModelTwoEnumDto, val test: Int)

@DomainModel
enum class ModelTwoEnumDomainModel {
    Enabled,
    Disabled
}

@DomainModel
data class ModelTwoDomainModel(val id: String, val enum: ModelTwoEnumDomainModel, val test: String)

@DTO(MapWith.Value)
enum class ModelTwoEnumValueDto(val value: Int) {
    Enabled_(1),
    Disabled_(2)
}

@DomainModel(MapWith.Value)
enum class ModelTwoEnumValueDomainModel(val value: Int) {
    Enabled(1),
    Disabled(2)
}

@DTO
data class ModelTwoWithAnotherModelDto(val id: String, val model: ModelOneDto, val test: Int)

@DomainModel
data class ModelTwoWithAnotherModelDomainModel(val id: String, val model: ModelOneDomainModel, val test: String)