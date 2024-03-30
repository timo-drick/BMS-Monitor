package de.drick.bmsmonitor

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index

class BmsStore private constructor(ctx: Context) {
    //private val db:
}

@Dao
interface KnownBmsDao {

}

@Entity(indices = [Index(value = ["id"])])
data class BmsInfo(
    val id: String,
    val address: String,
    
)