package com.bdavidgm.notasrec

import android.app.Application
import com.bdavidgm.notasrec.data.NotasRepository
import com.bdavidgm.notasrec.data.local.AppDatabase

class NotasApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val repository: NotasRepository by lazy {
        NotasRepository(
            dao = database.notasDao(),
            appContext = applicationContext,
        )
    }
}
