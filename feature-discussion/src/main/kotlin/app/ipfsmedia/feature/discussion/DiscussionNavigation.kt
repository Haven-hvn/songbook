package app.ipfsmedia.feature.discussion

import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.core.navigation.ThreadRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object DiscussionNavigation {
    @IntoSet
    @Provides
    fun provideDiscussionEntries(): EntryProviderInstaller = {
        entry<ThreadRoute> { key -> ThreadScreen(threadKey = key.threadKey) }
    }
}
