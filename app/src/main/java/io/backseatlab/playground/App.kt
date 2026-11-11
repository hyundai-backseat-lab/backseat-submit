package io.backseatlab.playground

import android.app.Application
import io.backseatlab.playground.di.AppContainer

/** 앱 프로세스가 처음 뜰 때 한 번 실행된다. 객체를 만들어 연결하고 차량 연결을 시작한다. */
class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
