package app.ipfsmedia.feature.nowplaying

import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.core.navigation.NowPlayingRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object NowPlayingNavigation {
    @IntoSet
    @Provides
    fun provideNowPlayingEntries(): EntryProviderInstaller = {
        entry<NowPlayingRoute> { NowPlayingScreen() }
    }
}
