package com.dengerin.app.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Credential Manager membutuhkan Activity context, bukan application context. */
fun Context.findActivityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivityOrNull()
    else -> null
}
