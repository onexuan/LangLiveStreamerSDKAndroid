package net.lang.streamer.faceu.image

import android.graphics.Bitmap

/**
 * Created by Rayan on 2019/7/17.
 */

class ImageFrame(private var im: Bitmap, private var del: Int) {

    var image: Bitmap? = im

    var delay = del

    var nextFrame: ImageFrame? = null
}