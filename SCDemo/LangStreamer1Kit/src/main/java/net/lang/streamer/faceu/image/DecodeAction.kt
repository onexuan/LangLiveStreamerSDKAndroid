package net.lang.streamer.faceu.image

/**
 * Created by Rayan on 2019/7/17.
 */

interface DecodeAction {

    fun parseOk(parseStatus: Boolean, frameIndex: Int)
}