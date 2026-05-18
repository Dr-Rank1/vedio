package com.videosaver

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule

@GlideModule
class GlideConfig : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val memoryCacheSize = (0.15 * Runtime.getRuntime().maxMemory()).toInt()
        builder.setMemoryCache(LruResourceCache(memoryCacheSize.toLong()))
    }

    override fun isManifestParsingEnabled() = false
}
