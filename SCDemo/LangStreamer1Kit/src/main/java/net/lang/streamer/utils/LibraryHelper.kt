package net.lang.streamer.utils

/**
 * Created by Rayan on 2019/7/10.
 *
 * Singleton instance
 */

class LibraryHelper private constructor() {

    companion object {

        val instance: LibraryHelper by lazy { LibraryHelper() }

        private var isWebpLibLoaded = false

        fun loadWebpLibOnce() {
            if (!isWebpLibLoaded) {
                System.loadLibrary("webpanim")
                isWebpLibLoaded = true
            }
        }
    }
}