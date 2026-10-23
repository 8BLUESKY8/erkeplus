package gitee.leolin_me.erkepp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * 全局 ImageLoader。
 *
 * 官方不限制头像的文件类型（实测可以传动图），所以这里把 GIF 解码器挂上：
 * Android 9 及以上走 [ImageDecoderDecoder]，更低的版本退回 [GifDecoder]。
 * 不挂的话动图只会显示第一帧。
 */
class ErkeppApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
