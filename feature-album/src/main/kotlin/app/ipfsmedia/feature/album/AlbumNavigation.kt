package app.ipfsmedia.feature.album

import app.ipfsmedia.core.navigation.AlbumRoute
import app.ipfsmedia.core.navigation.EntryProviderInstaller
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object AlbumNavigation {
    @IntoSet
    @Provides
    fun provideAlbumEntries(): EntryProviderInstaller = {
        entry<AlbumRoute> { key -> AlbumScreen(recordKey = key.recordKey) }
    }
}
