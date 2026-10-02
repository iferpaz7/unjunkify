package com.unjunkify.data.repository

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentTime(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
