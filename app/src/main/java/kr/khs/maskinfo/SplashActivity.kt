package kr.khs.maskinfo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import org.jetbrains.anko.startActivity

class SplashActivity : AppCompatActivity() {
    private var isRunning = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_splash)

        startActivity<MainActivity>()
        finish()
//        val splash = SplashThread()
//        splash.run()
    }

    inner class SplashThread : Thread() {
        override fun run() {
//            while(isRunning) {
            SystemClock.sleep(1500)
            startActivity<MainActivity>()
            finish()
//            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}