package com.hbzhou.open.flowcamera

import android.util.Size
import java.lang.Long.signum

/**
 * author hbzhou
 * date 2019/12/13 10:53
 */
class CompareSizesByArea : Comparator<Size> {

    // We cast here to ensure the multiplications won't overflow
    override fun compare(lhs: Size, rhs: Size) =
        signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
}