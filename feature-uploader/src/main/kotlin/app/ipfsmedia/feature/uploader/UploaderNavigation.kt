package app.ipfsmedia.feature.uploader

import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.core.navigation.UploaderRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object UploaderNavigation {
    @IntoSet
    @Provides
    fun provideUploaderEntries(): EntryProviderInstaller = {
        entry<UploaderRoute> { key -> UploaderScreen(address = key.address) }
    }
}
