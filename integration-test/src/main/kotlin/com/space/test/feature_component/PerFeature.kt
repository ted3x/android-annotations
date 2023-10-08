package com.space.test.feature_component

abstract class PerFeature {

    abstract val featureQualifer: String
    private var _module : Module? = null

    abstract fun onFeatureModule() : Module

    fun getModule() = _module

    fun inject(fm : ((Module) -> Unit)? = null) {
    }

    fun destroy() {
    }
}