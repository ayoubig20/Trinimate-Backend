package com.trinimate.di

import com.trinimate.security.PasswordHasher
import com.trinimate.services.*
import org.koin.dsl.module

val appModule = module {
    single { PasswordHasher() }
    single { AuthService(get()) }
    single { PlayerService() }
    single { SessionService() }
    single { ChatService() }
    single { MiscService() }
}