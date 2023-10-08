# android-annotations

A collection of annotations written in Kotlin to facilitate code generation for DTO (Data Transfer Object) mapping. This repository leverages the KSP (Kotlin Symbol Processing) compiler to generate the necessary code based on annotations.

## Description

This repository provides annotations to mark classes and fields for DTO/Domain mapping. The annotations facilitate automatic code generation for DTOs and their corresponding domain models. 

## Installation & Setup

```
dependencies {
  implementation 'com.github.ted3x.android-annotations:annotations:0.0.1'
  ksp 'com.github.ted3x.android-annotations:compiller:0.0.1'
}
```

## Usage

### Annotations

- **@DTO**: Marks a class to be considered as a DTO. It takes an optional parameter to specify mapping strategy with `mapWith` which defaults to `MapWith.Name`, which will be used for Enums only.

- **@MapsTo**: Specifies the target domain class that the DTO maps to. Used for many to one relationship between multiple DTO models and single Domain Model
 
- **@DomainModel**: Marks a class as a domain model. Similar to the `@DTO`, it takes an optional `mapWith` parameter.

- **@DTOFieldName**: Annotates a DTO field to specify its name when mapped.

- **@DomainFieldName**: Annotates a domain field to specify its name when mapped.

- **MapWith**: Enum to specify the mapping strategy. It can either be `Value` or `Name`.

### Example

```kotlin
@DTO(mapWith = MapWith.Value)
data class UserDTO(
    @DomainFieldName("id")
    val userId: Int,
    val name: String
)

@DomainModel
data class User(
    @DTOFieldName("userId")
    val id: Int,
    val name: String
)
```

## Contributing

If you'd like to contribute, please fork the repository and create a pull request. For major changes, please open an issue first to discuss what you'd like to change.

## License

This project is licensed under the MIT License - see the `LICENSE` file for details.

## Credits

Developed by [Your Name/Username]. Special thanks to [collaborators/contributors].
