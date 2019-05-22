// Copyright MyScript. All rights reserved.

package com.myscript.iink.demo

import android.app.Application
import android.content.Context
import android.util.Log
import com.myscript.certificate.MyCertificate
import com.myscript.iink.Engine

class IInkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        context = this
    }

    companion object {
        private var engine: Engine? = null
        private var context: Context? = null

        @Synchronized
        fun getEngine(): Engine {
            if (engine == null) {
                engine = Engine.create(MyCertificate.getBytes())
            }

            val configDir = "zip://" + context!!.packageCodePath + "!/assets/conf"
            Log.d(Consts.TAG, "config Dir: $configDir")
            val config = engine!!.configuration
            config.setStringArray("configuration-manager.search-path", arrayOf(configDir))
            config.setString("lang", "zh_CN")
            config.setBoolean("gesture.enable", false)

            return engine as Engine
        }
    }

}
