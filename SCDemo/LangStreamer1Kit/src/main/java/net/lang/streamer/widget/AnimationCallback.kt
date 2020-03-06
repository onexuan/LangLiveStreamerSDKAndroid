package net.lang.streamer.widget


/**
 * Created by Rayan on 2019/7/17.
 */

interface AnimationCallback {

    /**
     * !!Important!!
     * To add the motion sticker success, MUST wait until this callback function invoked
     */
    fun onDecodeSuccess()

    fun onDecodeError()

    fun onAnimationPlayFinish()
}