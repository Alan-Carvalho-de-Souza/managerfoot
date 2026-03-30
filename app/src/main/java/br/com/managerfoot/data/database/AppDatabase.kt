package br.com.managerfoot.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
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
        EstadioEntity::class,
    ],
    version = 8,
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
    abstract fun estadioDao(): EstadioDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "managerfoot.db"
                )
                    //.createFromAsset("database/seed.db")   // dados iniciais (times, jogadores)
                    .fallbackToDestructiveMigration()       // trocar por Migration em produção
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

    @TypeConverter fun formatoToString(f: FormatoCampeonato): String = f.name
    @TypeConverter fun stringToFormato(s: String): FormatoCampeonato = FormatoCampeonato.valueOf(s)

    @TypeConverter fun tipoEventoToString(t: TipoEvento): String = t.name
    @TypeConverter fun stringToTipoEvento(s: String): TipoEvento = TipoEvento.valueOf(s)

    @TypeConverter fun tipoTransfToString(t: TipoTransferencia): String = t.name
    @TypeConverter fun stringToTipoTransf(s: String): TipoTransferencia = TipoTransferencia.valueOf(s)
}
