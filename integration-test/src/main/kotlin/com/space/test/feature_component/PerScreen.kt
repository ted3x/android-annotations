//package com.space.core.presentation.di.koin_scope
//
//import androidx.lifecycle.Lifecycle
//import androidx.lifecycle.LifecycleObserver
//import androidx.lifecycle.LifecycleOwner
//import androidx.lifecycle.OnLifecycleEvent
//import com.space.core.presentation.di.module.SPScreenModule
//import org.koin.core.context.loadKoinModules
//import org.koin.core.context.unloadKoinModules
//import org.koin.core.module.Module
//
//abstract class PerScreen<L: LifecycleOwner> : SPScreenModule<L>, LifecycleObserver{
//    private lateinit var module: Module
//
//    abstract fun onScreenModule(): Module
//
//    override fun inject(lifecycleOwner : L) {
//        module = onScreenModule()
//        loadKoinModules(module)
//        lifecycleOwner.lifecycle.addObserver(this)
//    }
//
//    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
//    override fun destroy(lifecycleOwner: LifecycleOwner) {
//        unloadKoinModules(module)
//        lifecycleOwner.lifecycle.removeObserver(this)
//    }
//}