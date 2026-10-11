package io.github.tukiza7_debug.vibecast.addonx.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine
import io.github.tukiza7_debug.vibecast.adblock.FilterEngine.SitePolicy
import io.github.tukiza7_debug.vibecast.adblock.net.AdBlockInterceptor
import io.github.tukiza7_debug.vibecast.adblock.prefs.AdBlockSettings
import io.github.tukiza7_debug.vibecast.core.http.HttpClient
import io.github.tukiza7_debug.vibecast.core.http.HostFilter
import io.github.tukiza7_debug.vibecast.core.http.OkHttpFetcher
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(engine: FilterEngine, settings: AdBlockSettings): OkHttpClient {
        fun currentPolicy(): SitePolicy = try {
            runBlocking { SitePolicy(disabledHosts = settings.disabledHosts.first()) }
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
    fun provideHostFilter(engine: FilterEngine): HostFilter = HostFilter { url ->
        engine.decide(
            io.github.tukiza7_debug.vibecast.adblock.rules.BlockRequest(url, null, null),
            SitePolicy(),
        ) is io.github.tukiza7_debug.vibecast.adblock.rules.Decision.Block
    }
}
