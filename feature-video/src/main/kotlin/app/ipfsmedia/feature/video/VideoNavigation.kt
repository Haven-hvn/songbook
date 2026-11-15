package app.ipfsmedia.feature.video

import androidx.compose.runtime.staticCompositionLocalOf
import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.core.navigation.VideoRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/**
 * How a video page asks the shell for full screen. The shell owns full screen (it hides the
 * sidebar and the bars, and handles F and Escape); a page only asks.
 */
val LocalRequestFullscreen = staticCompositionLocalOf<() -> Unit> { {} }

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object VideoNavigation {
    @IntoSet
    @Provides
    fun provideVideoEntries(): EntryProviderInstaller = {
        entry<VideoRoute> { key ->
            VideoScreen(recordKey = key.recordKey, onFullscreen = LocalRequestFullscreen.current)
        }
    }
}
