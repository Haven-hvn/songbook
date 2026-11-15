package app.ipfsmedia.feature.home

import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.core.navigation.HomeRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object HomeNavigation {
    @IntoSet
    @Provides
    fun provideHomeEntries(): EntryProviderInstaller = {
        entry<HomeRoute> { HomeScreen() }
    }
}
