package app.ipfsmedia.feature.playlists

import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.core.navigation.PlaylistRoute
import app.ipfsmedia.core.navigation.PlaylistsRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object PlaylistsNavigation {
    @IntoSet
    @Provides
    fun providePlaylistEntries(): EntryProviderInstaller = {
        entry<PlaylistsRoute> { PlaylistsScreen() }
        entry<PlaylistRoute> { key -> PlaylistScreen(playlistId = key.playlistId) }
    }
}
