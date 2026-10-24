package gitee.leolin_me.erkepp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import gitee.leolin_me.erkepp.ui.profile.trimAvatarCache

/**
 * 全局 ImageLoader。
 *
 * 官方不限制头像的文件类型（实测可以传动图），所以这里把 GIF 解码器挂上：
 * Android 9 及以上走 [ImageDecoderDecoder]，更低的版本退回 [GifDecoder]。
 * 不挂的话动图只会显示第一帧。
 */
class ErkeppApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // 头像缓存以前只写不删（实测能到 81MB），升级后启动就收回上限内
        val context = applicationContext
        Thread {
            runCatching { trimAvatarCache(context) }
        }.apply { isDaemon = true }.start()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            // Coil 默认按「剩余空间 2%」算磁盘缓存（上限 512MB），对只用来存活动封面来说太大。
            // 定成 64MB：够放下上千张卡片的封面，也不会在存储里悄悄长到几百兆。
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
}
