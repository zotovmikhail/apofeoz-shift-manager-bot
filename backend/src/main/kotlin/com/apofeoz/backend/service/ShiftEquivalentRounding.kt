package com.apofeoz.backend.service

import kotlin.math.round

/** Доля смены (часы / норма) — до 3 знаков после запятой. */
fun Double.roundShiftEquivalentToThreeDecimals(): Double =
    round(this * 1000.0) / 1000.0
