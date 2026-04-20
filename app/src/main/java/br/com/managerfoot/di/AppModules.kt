package br.com.managerfoot.di

import android.content.Context
import androidx.room.Room
import br.com.managerfoot.data.dao.*
import br.com.managerfoot.data.database.AppDatabase
import br.com.managerfoot.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ─────────────────────────────────────────────
//  DatabaseModule
//  Provê a instância do Room e todos os DAOs
// ─────────────────────────────────────────────
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides fun provideTimeDao(db: AppDatabase): TimeDao = db.timeDao()
    @Provides fun provideJogadorDao(db: AppDatabase): JogadorDao = db.jogadorDao()
    @Provides fun provideCampeonatoDao(db: AppDatabase): CampeonatoDao = db.campeonatoDao()
    @Provides fun providePartidaDao(db: AppDatabase): PartidaDao = db.partidaDao()
    @Provides fun provideClassificacaoDao(db: AppDatabase): ClassificacaoDao = db.classificacaoDao()
    @Provides fun provideFinancaDao(db: AppDatabase): FinancaDao = db.financaDao()
    @Provides fun provideHallDaFamaDao(db: AppDatabase): HallDaFamaDao = db.hallDaFamaDao()
    @Provides fun provideRankingGeralDao(db: AppDatabase): RankingGeralDao = db.rankingGeralDao()
    @Provides fun provideEstadioDao(db: AppDatabase): EstadioDao = db.estadioDao()
    @Provides fun providePropostaIADao(db: AppDatabase): PropostaIADao = db.propostaIADao()
}

// ─────────────────────────────────────────────
//  RepositoryModule
//  Provê os repositórios (todos @Singleton via
//  anotação na própria classe)
// ─────────────────────────────────────────────
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // Hilt injeta automaticamente via @Singleton + @Inject constructor
    // Nenhum @Provides necessário para as classes anotadas
}
