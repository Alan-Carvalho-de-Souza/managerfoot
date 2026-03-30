package br.com.managerfoot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.managerfoot.data.database.entities.EstadioEntity

@Dao
interface EstadioDao {

    @Query("SELECT * FROM estadio WHERE timeId = :timeId")
    suspend fun buscarPorTime(timeId: Int): EstadioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(estadio: EstadioEntity)

    @Query("DELETE FROM estadio")
    suspend fun deleteAll()
}
