package br.com.managerfoot.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.managerfoot.data.dao.*
import br.com.managerfoot.data.database.entities.*

@Database(
    entities = [
        TimeEntity::class,
        JogadorEntity::class,
        CampeonatoEntity::class,
        CampeonatoTimeEntity::class,
        TemporadaEntity::class,
        PartidaEntity::class,
        EscalacaoEntity::class,
        EventoPartidaEntity::class,
        ClassificacaoEntity::class,
        FinancaEntity::class,
        TransferenciaEntity::class,
        HallDaFamaEntity::class,
        RankingGeralEntity::class,
        RankingConmebolEntity::class,
        RankingBrasileiroEntity::class,
        RankingEuropaEntity::class,
        EstadioEntity::class,
        PropostaIAEntity::class,
        PropostaClubeEntity::class,
        TecnicoEntity::class,
        PassagemTecnicoEntity::class,
    ],
    version = 35,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun timeDao(): TimeDao
    abstract fun jogadorDao(): JogadorDao
    abstract fun campeonatoDao(): CampeonatoDao
    abstract fun partidaDao(): PartidaDao
    abstract fun classificacaoDao(): ClassificacaoDao
    abstract fun financaDao(): FinancaDao
    abstract fun hallDaFamaDao(): HallDaFamaDao
    abstract fun rankingGeralDao(): RankingGeralDao
    abstract fun rankingConmebolDao(): RankingConmebolDao
    abstract fun rankingBrasileiroDao(): RankingBrasileiroDao
    abstract fun rankingEuropaDao(): RankingEuropaDao
    abstract fun estadioDao(): EstadioDao
    abstract fun propostaIADao(): PropostaIADao
    abstract fun propostaClubeDao(): PropostaClubeDao
    abstract fun tecnicoDao(): TecnicoDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // 26 → 27: coluna janelaUltimaTransferencia (anti-revenda IA na mesma janela).
        // Migração não-destrutiva para preservar saves em andamento.
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE jogadores ADD COLUMN janelaUltimaTransferencia INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // 27 → 28: coluna despesaBase (custo de formação de juniores). Não-destrutiva.
        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE financas ADD COLUMN despesaBase INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        // 28 → 29: preços de ingresso por setor no estádio. Não-destrutiva
        // (0 = não inicializado; o EstadioRepository preenche pelo preço-base do clube).
        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE estadio ADD COLUMN precoArquibancada INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE estadio ADD COLUMN precoCadeira INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE estadio ADD COLUMN precoCamarote INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 29 → 30: coluna potencial (teto de evolução). Não-destrutiva, com BACKFILL
        // dos jogadores existentes: potencial = força + bônus por juventude (limitado a 99).
        // Veteranos (25+) já atingiram o teto → potencial = força.
        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jogadores ADD COLUMN potencial INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE jogadores SET potencial = MIN(99, forca + CASE
                        WHEN idade <= 18 THEN 12
                        WHEN idade <= 21 THEN 8
                        WHEN idade <= 24 THEN 4
                        ELSE 0
                    END)
                    """.trimIndent()
                )
            }
        }

        // 30 → 31: tabela ranking_conmebol (lançamentos de pontos por ano/competição).
        // Não-destrutiva: saves em andamento começam com o ranking vazio e passam a
        // pontuar a partir da próxima temporada encerrada.
        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ranking_conmebol (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timeId INTEGER NOT NULL,
                        ano INTEGER NOT NULL,
                        competicao TEXT NOT NULL,
                        motivo TEXT NOT NULL DEFAULT '',
                        pontos REAL NOT NULL DEFAULT 0.0,
                        deprecia INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ranking_conmebol_timeId ON ranking_conmebol(timeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ranking_conmebol_ano ON ranking_conmebol(ano)")
            }
        }

        // 31 → 32: tabela ranking_brasileiro (Ranking Nacional de Clubes da CBF).
        // Não-destrutiva: saves em andamento começam com o ranking vazio e passam a
        // pontuar a partir da próxima temporada encerrada. Como a janela do RNC é de
        // 5 anos, o ranking fica completo após cinco temporadas.
        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ranking_brasileiro (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timeId INTEGER NOT NULL,
                        ano INTEGER NOT NULL,
                        competicao TEXT NOT NULL,
                        motivo TEXT NOT NULL DEFAULT '',
                        pontos INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ranking_brasileiro_timeId ON ranking_brasileiro(timeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ranking_brasileiro_ano ON ranking_brasileiro(ano)")
            }
        }

        // 32 → 33: tabela ranking_europa (Coeficiente de Clubes da UEFA).
        // Não-destrutiva. A tabela nasce vazia e continua vazia até que Champions,
        // Europa e Conference League existam no jogo: esse conjunto de regras pontua
        // exclusivamente competições da UEFA, e nenhuma delas está implementada.
        private val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ranking_europa (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timeId INTEGER NOT NULL,
                        ano INTEGER NOT NULL,
                        competicao TEXT NOT NULL,
                        motivo TEXT NOT NULL DEFAULT '',
                        pontos REAL NOT NULL DEFAULT 0.0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ranking_europa_timeId ON ranking_europa(timeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ranking_europa_ano ON ranking_europa(ano)")
            }
        }

        // 33 → 34: coluna grauLesao em jogadores (1 leve/2 moderada/3 grave; 0 = sem lesão).
        // Não-destrutiva. Lesionados de saves antigos ficam com grau 0 (desconhecido) até a
        // próxima lesão; os jogos restantes continuam em partidasSemJogar.
        private val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jogadores ADD COLUMN grauLesao INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tecnicos ADD COLUMN aposentado INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "managerfoot.db"
                )
                    //.createFromAsset("database/seed.db")   // dados iniciais (times, jogadores)
                    .addMigrations(
                        MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29,
                        MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32,
                        MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35
                    )
                    .fallbackToDestructiveMigration()       // fallback p/ versões sem caminho de migração
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

// TypeConverters para enums e tipos não suportados nativamente pelo Room
class Converters {
    @TypeConverter fun posicaoToString(p: Posicao): String = p.name
    @TypeConverter fun stringToPosicao(s: String): Posicao = Posicao.valueOf(s)
    @TypeConverter fun posicaoOptToString(p: Posicao?): String? = p?.name
    @TypeConverter fun stringOptToPosicao(s: String?): Posicao? = s?.let { Posicao.valueOf(it) }

    @TypeConverter fun setorToString(s: Setor): String = s.name
    @TypeConverter fun stringToSetor(s: String): Setor = Setor.valueOf(s)

    @TypeConverter fun moraleToString(m: MoraleEstado): String = m.name
    @TypeConverter fun stringToMorale(s: String): MoraleEstado = MoraleEstado.valueOf(s)

    @TypeConverter fun estiloToString(e: EstiloJogo): String = e.name
    @TypeConverter fun stringToEstilo(s: String): EstiloJogo = EstiloJogo.valueOf(s)

    @TypeConverter fun tipoCampToString(t: TipoCampeonato): String = t.name
    @TypeConverter fun stringToTipoCamp(s: String): TipoCampeonato = TipoCampeonato.valueOf(s)

    @TypeConverter fun statusPropostaToString(s: StatusProposta): String = s.name
    @TypeConverter fun stringToStatusProposta(s: String): StatusProposta = StatusProposta.valueOf(s)

    @TypeConverter fun tipoPropostaToString(t: TipoProposta): String = t.name
    @TypeConverter fun stringToTipoProposta(s: String): TipoProposta = TipoProposta.valueOf(s)

    @TypeConverter fun formatoToString(f: FormatoCampeonato): String = f.name
    @TypeConverter fun stringToFormato(s: String): FormatoCampeonato = FormatoCampeonato.valueOf(s)

    @TypeConverter fun tipoEventoToString(t: TipoEvento): String = t.name
    @TypeConverter fun stringToTipoEvento(s: String): TipoEvento = TipoEvento.valueOf(s)

    @TypeConverter fun tipoTransfToString(t: TipoTransferencia): String = t.name
    @TypeConverter fun stringToTipoTransf(s: String): TipoTransferencia = TipoTransferencia.valueOf(s)

    @TypeConverter fun statusPropostaClubeToString(s: StatusPropostaClube): String = s.name
    @TypeConverter fun stringToStatusPropostaClube(s: String): StatusPropostaClube = StatusPropostaClube.valueOf(s)
}
