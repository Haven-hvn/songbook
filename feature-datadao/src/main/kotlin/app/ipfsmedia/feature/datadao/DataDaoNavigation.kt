package app.ipfsmedia.feature.datadao

import app.ipfsmedia.core.navigation.DataDaoRoute
import app.ipfsmedia.core.navigation.DataDaosRoute
import app.ipfsmedia.core.navigation.EntryProviderInstaller
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet

/** Registers this module's screens with the app's navigation (the `modular/hilt` recipe). */
@Module
@InstallIn(ActivityRetainedComponent::class)
object DataDaoNavigation {
    @IntoSet
    @Provides
    fun provideDataDaoEntries(): EntryProviderInstaller = {
        entry<DataDaosRoute> { DataDaosScreen() }
        entry<DataDaoRoute> { key -> DataDaoScreen(daoKey = key.daoKey, initialTab = key.tab) }
    }
}
