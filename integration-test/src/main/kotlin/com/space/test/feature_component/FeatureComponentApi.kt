package com.space.test.feature_component

import com.space.annotations.FeatureComponent

class Fragment()

class FeatureModule(override val featureQualifer: String) : PerFeature() {
    override fun onFeatureModule(): Module {
        TODO("Not yet implemented")
    }
}

@FeatureComponent(qualifier = Fragment::class, featureModule = FeatureModule::class)
interface FeatureComponentApi : SPBaseFeatureComponent {

    fun starterModule(): StarterModule
    fun completionModule(): CompletionModule
}

interface SPBaseFeatureComponent {

    val featureModule: PerFeature
    fun featureInject(fm: ((Module) -> Unit)? = null) = featureModule.inject(fm)
    fun featureDestroy()
    fun featureQualifer(): String
}

class StarterModule(override val qualifier: String) : Module
class CompletionModule(override val qualifier: String) : Module

interface Module {
    val qualifier: String
}