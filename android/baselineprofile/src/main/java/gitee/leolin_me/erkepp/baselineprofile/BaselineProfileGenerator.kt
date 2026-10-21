package gitee.leolin_me.erkepp.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成 Baseline Profile：真机上跑一遍冷启动 + 发现页滑动，把热点方法记进 profile。
 *
 * 跑法：`cd android; .\gradlew.bat :app:generateReleaseBaselineProfile`
 * 生成物落在 `app/src/release/generated/baselineProfiles/`，会一起打进 APK。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)

            // 发现页滑动：覆盖卡片组合、封面解码和分页请求
            repeat(2) {
                val list = device.findObject(By.scrollable(true)) ?: return@repeat
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "gitee.leolin_me.erkepp"
    }
}
