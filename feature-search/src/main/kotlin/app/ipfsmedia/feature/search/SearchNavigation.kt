package app.ipfsmedia.feature.search

import app.ipfsmedia.core.navigation.EntryProviderInstaller
import app.ipfsmedia.core.navigation.SearchRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object SearchNavigation {
    @IntoSet
    @Provides
    fun provideSearchEntries(): EntryProviderInstaller = {
        entry<SearchRoute> { SearchScreen() }
    }
}
