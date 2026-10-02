package com.unjunkify.scanner

import com.unjunkify.data.JunkSnapshot

interface StorageScanner {
    suspend fun scan(): List<JunkSnapshot>
    fun categories(): Set<String>
}
