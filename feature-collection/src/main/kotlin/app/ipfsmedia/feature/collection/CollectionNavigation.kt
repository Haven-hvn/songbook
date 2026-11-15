package app.ipfsmedia.feature.collection

import app.ipfsmedia.core.navigation.CollectionRoute
import app.ipfsmedia.core.navigation.EntryProviderInstaller
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object CollectionNavigation {
    @IntoSet
    @Provides
    fun provideCollectionEntries(): EntryProviderInstaller = {
        entry<CollectionRoute> { CollectionScreen() }
    }
}
