package com.unjunkify.di

import com.unjunkify.data.repository.BatteryRepository
import com.unjunkify.data.repository.BatteryRepositoryImpl
import com.unjunkify.data.repository.ExemptRepository
import com.unjunkify.data.repository.ExemptRepositoryImpl
import com.unjunkify.data.repository.HealthReportRepository
import com.unjunkify.data.repository.HealthReportRepositoryImpl
import com.unjunkify.ui.CleanViewModel
import com.unjunkify.ui.HealthViewModel
import com.unjunkify.utils.ExemptCache
import org.koin.dsl.module

val appModule = module {
    single { ExemptCache() }
    single<ExemptRepository> { ExemptRepositoryImpl(get()) }
    single<HealthReportRepository> { HealthReportRepositoryImpl(get(), get()) }
    single<BatteryRepository> { BatteryRepositoryImpl(get()) }
    factory { CleanViewModel(get(), get(), get(), get(), get(), get(), get()) }
    factory { HealthViewModel(get(), get(), get(), get(), alertStore = get()) }
}
