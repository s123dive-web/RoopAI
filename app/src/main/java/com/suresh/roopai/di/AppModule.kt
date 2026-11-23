package com.suresh.roopai.di

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.suresh.roopai.data.HistoryRepository
import com.suresh.roopai.data.ProjectStore
import com.suresh.roopai.data.db.EditDao
import com.suresh.roopai.data.db.RoopDatabase
import com.suresh.roopai.data.settings.KeystoreSecretStore
import com.suresh.roopai.data.settings.SecretStore
import com.suresh.roopai.data.settings.SettingsRepository
import com.suresh.roopai.engine.cloud.CloudEngine
import com.suresh.roopai.engine.cloud.GeminiProvider
import com.suresh.roopai.engine.cloud.ProviderRegistry
import com.suresh.roopai.engine.local.FaceAnalysisSource
import com.suresh.roopai.engine.local.FaceAnalyzer
import com.suresh.roopai.ui.editor.EditorGateway
import com.suresh.roopai.ui.editor.EditorGatewayImpl
import com.suresh.roopai.ui.home.PhotoImporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): RoopDatabase =
        Room.databaseBuilder(context, RoopDatabase::class.java, RoopDatabase.NAME)
            .addMigrations(*RoopDatabase.MIGRATIONS)
            .build()

    @Provides
    fun editDao(db: RoopDatabase): EditDao = db.edits()

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides @Singleton
    fun secretStore(impl: KeystoreSecretStore): SecretStore = impl

    @Provides @Singleton
    fun settings(store: DataStore<Preferences>, secrets: SecretStore) = SettingsRepository(store, secrets)

    @Provides @Singleton
    fun history(dao: EditDao, projects: ProjectStore): HistoryRepository =
        HistoryRepository(
            dao,
            object : HistoryRepository.ProjectFiles {
                override suspend fun delete(projectId: String) { projects.delete(projectId) }
                override suspend fun deleteAll() { projects.deleteAll() }
                override suspend fun prune(projectId: String, keep: Set<String>) { projects.prune(projectId, keep) }
            },
        )

    @Provides @Singleton
    fun editorGateway(impl: EditorGatewayImpl): EditorGateway = impl

    @Provides
    fun photoImporter(projects: ProjectStore): PhotoImporter = PhotoImporter { uri -> projects.importFrom(uri.toUri()) }

    @Provides @Singleton
    fun analysisSource(impl: FaceAnalyzer): FaceAnalysisSource = impl

    /** Only CloudEngine uses this client; nothing else in the app touches the network. */
    @Provides @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Provides @Singleton
    fun providers(client: OkHttpClient): ProviderRegistry = ProviderRegistry(listOf(GeminiProvider(client)))

    @Provides @Singleton
    fun cloudEngine(registry: ProviderRegistry, settings: SettingsRepository): CloudEngine =
        CloudEngine(registry, config = { settings.cloudConfig(registry.default.id) })
}
