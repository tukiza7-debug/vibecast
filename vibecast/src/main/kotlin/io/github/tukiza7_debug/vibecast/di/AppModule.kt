package io.github.tukiza7_debug.vibecast.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine.SitePolicy
import io.github.tukiza7_debug.vibecast.adblock.net.AdBlockInterceptor
import io.github.tukiza7_debug.vibecast.adblock.prefs.AdBlockSettings
import io.github.tukiza7_debug.vibecast.adblock.web.SniffingWebView
import io.github.tukiza7_debug.vibecast.core.engine.WebSniffer
import io.github.tukiza7_debug.vibecast.core.http.HostFilter
import io.github.tukiza7_debug.vibecast.core.http.HttpClient
import io.github.tukiza7_debug.vibecast.core.http.OkHttpFetcher
import io.github.tukiza7_debug.vibecast.player.HeadersRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        engine: FilterEngine,
        settings: AdBlockSettings,
        headersRegistry: HeadersRegistry,
    ): OkHttpClient {
        fun currentPolicy(): SitePolicy =
            try {
                runBlocking {
                    SitePolicy(
                        disabledHosts = settings.disabledHosts.first(),
                        bypassHosts = settings.bypassHosts.first() + headersRegistry.requiredHosts(),
                    )
                }
            } catch (_: Exception) {
                SitePolicy()
            }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(AdBlockInterceptor(engine) { currentPolicy() })
            .build()
    }

    @Provides
    @Singleton
    fun provideHttpClient(okHttpClient: OkHttpClient): HttpClient = OkHttpFetcher(okHttpClient)

    @Provides
    @Singleton
    fun provideHostFilter(engine: FilterEngine): HostFilter =
        HostFilter { url ->
            engine.decide(
                io.github.tukiza7_debug.vibecast.adblock.rules.BlockRequest(url, null, null),
                SitePolicy(),
            ) is io.github.tukiza7_debug.vibecast.adblock.rules.Decision.Block
        }

    @Provides
    @Singleton
    fun provideWebSniffer(
        @ApplicationContext context: Context,
        engine: FilterEngine,
    ): WebSniffer = SniffingWebView(context, engine) { SitePolicy() }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): io.github.tukiza7_debug.vibecast.data.db.AppDatabase =
        androidx.room.Room.databaseBuilder(
            context,
            io.github.tukiza7_debug.vibecast.data.db.AppDatabase::class.java,
            "vibecast.db",
        ).build()

    @Provides
    fun provideExtensionDao(
        db: io.github.tukiza7_debug.vibecast.data.db.AppDatabase,
    ): io.github.tukiza7_debug.vibecast.data.db.ExtensionDao = db.extensionDao()

    @Provides
    fun provideFavoritesDao(
        db: io.github.tukiza7_debug.vibecast.data.db.AppDatabase,
    ): io.github.tukiza7_debug.vibecast.data.db.FavoritesDao = db.favoritesDao()

    @Provides
    fun provideHistoryDao(db: io.github.tukiza7_debug.vibecast.data.db.AppDatabase): io.github.tukiza7_debug.vibecast.data.db.HistoryDao =
        db.historyDao()
}
