package com.example.jalraksha.ui.main

import androidx.annotation.StringRes
import com.example.jalraksha.R

/** The four destinations on the bottom bar, in the order the design draws them. */
enum class MainTab(@param:StringRes val labelRes: Int) {
    Home(R.string.nav_home),
    Trends(R.string.nav_trends),
    Report(R.string.nav_report),
    Profile(R.string.nav_profile),
}
